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

package monix.connect.benchmarks.redis.rediculous

import cats.effect._
import fs2.io.tcp._
import io.chrisdavenport.rediculous._
import org.scalacheck.Gen

import scala.concurrent.ExecutionContext

trait RedisRedicolousBenchFixture {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val maxKey: Int = 5000
  val rnd = new scala.util.Random

  val redicolousConn =
    for {
      blocker <- Blocker[IO]
      sg      <- SocketGroup[IO](blocker)
      c       <- RedisConnection.queued[IO](sg, "localhost", 6379, maxQueued = 10000, workers = 2)
    } yield c

  type RedisIO[A] = Redis[IO, A]

  def flushdb =
    redicolousConn
      .use(c => RedisCommands.flushdb[RedisIO].run(c))
      .unsafeRunSync
  def getRandomString = Gen.alphaLowerStr.sample.get
}
