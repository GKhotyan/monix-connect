/*
 * Copyright (c) 2020-2020 by The Monix Connect Project Developers.
 * See the project homepage at: https://connect.monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.connect.dynamodb

import monix.execution.cancelables.AssignableCancelable
import monix.execution.{Ack, Callback, Scheduler}
import monix.reactive.Consumer
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{DynamoDbRequest, DynamoDbResponse}
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * A pre-built [[Consumer]] implementation that expects incoming [[DynamoDbRequest]] and
  * executes them with a retryable-with-delay strategy that makes it more flexible to unexpected failures.
  *
  * @param retriesPerOp indicates the number of times that an operation would be retried in case there was a failure
  *                     it must be higher or equal than 0.
  * @param dynamoDbOp an implicit [[DynamoDbOp]] that would abstract the execution of the specific operation.
  * @param delayAfterFailure delay after failure for the execution of a single [[DynamoDbOp]].
  * @param client an implicit instance of a [[DynamoDbAsyncClient]].
  * @tparam In a lower bounded type of [[DynamoDbRequest]] that represents the incoming elements.
  * @tparam Out The response of the execution as type parameter lower bounded by [[DynamoDbResponse]].
  */
private[dynamodb] class DynamoDbSubscriber[In <: DynamoDbRequest, Out <: DynamoDbResponse](
  retriesPerOp: Int,
  delayAfterFailure: Option[FiniteDuration])(
  implicit
  dynamoDbOp: DynamoDbOp[In, Out],
  client: DynamoDbAsyncClient)
  extends Consumer[In, Unit] {

  require(retriesPerOp >= 0, "Retries per operation must be higher or equal than 0.")

  override def createSubscriber(cb: Callback[Throwable, Unit], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    val sub = new Subscriber[In] {

      implicit val scheduler = s

      def onNext(request: In): Future[Ack] = {
        DynamoDbOp
          .create(request, retriesPerOp, delayAfterFailure)
          .redeem(ex => {
            onError(ex)
            Ack.Stop
          }, _ => {
            Ack.Continue
          })
          .runToFuture
      }

      def onComplete(): Unit = {
        cb.onSuccess(())
      }

      def onError(ex: Throwable): Unit = {
        cb.onError(ex)
      }
    }

    (sub, AssignableCancelable.single())
  }

}

/** Companion object of [[DynamoDbSubscriber]] class */
private[dynamodb] object DynamoDbSubscriber {
  def apply[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    retriesPerOp: Int,
    delayAfterFailure: Option[FiniteDuration])(
    implicit
    dynamoDbOp: DynamoDbOp[In, Out],
    client: DynamoDbAsyncClient): DynamoDbSubscriber[In, Out] =
    new DynamoDbSubscriber(retriesPerOp, delayAfterFailure)(dynamoDbOp, client)
}
