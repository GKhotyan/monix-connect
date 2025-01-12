package monix.connect.dynamodb

import java.lang.Thread.sleep

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import DynamoDbOp.Implicits._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class DynamoDbTransformerSuite
  extends AnyWordSpecLike with Matchers with ScalaFutures with DynamoDbFixture with BeforeAndAfterAll {

  implicit val defaultConfig: PatienceConfig = PatienceConfig(10.seconds, 300.milliseconds)
  implicit val client: DynamoDbAsyncClient = DynamoDbClient()

  s"${DynamoDb}.transformer() creates a transformer operator" that {

    s"given an implicit instance of ${DynamoDbOp.Implicits.createTableOp} in the scope" must {

      s"transform `CreateTableRequests` to `CreateTableResponses`" in {
        //given
        val randomTableName: String = genTableName.sample.get
        val transformer: Transformer[CreateTableRequest, Task[CreateTableResponse]] =
          DynamoDb.transformer[CreateTableRequest, CreateTableResponse]()
        val request =
          createTableRequest(tableName = randomTableName, schema = keySchema, attributeDefinition = tableDefinition)

        //when
        val ob: Observable[Task[CreateTableResponse]] =
          Observable
            .pure(request)
            .transform(transformer)
        val t: Task[CreateTableResponse] = ob.headL.runToFuture.futureValue

        //then
        whenReady(t.runToFuture) { response =>
          response shouldBe a[CreateTableResponse]
          response.tableDescription().hasKeySchema shouldBe true
          response.tableDescription().hasAttributeDefinitions shouldBe true
          response.tableDescription().hasGlobalSecondaryIndexes shouldBe false
          response.tableDescription().hasReplicas shouldBe false
          response.tableDescription().tableName() shouldEqual randomTableName
          response.tableDescription().keySchema() should contain theSameElementsAs keySchema
          response.tableDescription().attributeDefinitions() should contain theSameElementsAs tableDefinition
        }
      }
    }
      s"given an implicit instance of ${DynamoDbOp.Implicits.putItemOp} in the scope" must {

        s"transform a single`PutItemRequest` to `PutItemResponse` " in {
          //given
          val transformer: Transformer[PutItemRequest, Task[PutItemResponse]] =
            DynamoDb.transformer[PutItemRequest, PutItemResponse]()
          val city = Gen.nonEmptyListOf(Gen.alphaChar).sample.get.mkString
          val citizenId = genCitizenId.sample.get
          val debt = Gen.choose(0, 10000).sample.get
          val request: PutItemRequest = putItemRequest(tableName, city, citizenId, debt)

          //when
          val t: Task[PutItemResponse] = Observable.fromIterable(Iterable(request)).transform(transformer).headL.flatten

          //then
          whenReady(t.runToFuture) { r =>
            r shouldBe a[PutItemResponse]
            val getResponse: GetItemResponse = toScala(client.getItem(getItemRequest(tableName, city, citizenId))).futureValue
            getResponse.item().values().asScala.head.n().toDouble shouldBe debt
          }
        }

        s"transform `PutItemRequests` to `PutItemResponses` " in {
          //given
          val transformer: Transformer[PutItemRequest, Task[PutItemResponse]] =
            DynamoDb.transformer[PutItemRequest, PutItemResponse]()
          val requestAttr: List[(String, Int, Double)] = Gen.nonEmptyListOf(genRequestAttributes).sample.get
          val requests: List[PutItemRequest] = requestAttr.map { case (city, citizenId, debt) => putItemRequest(tableName, city, citizenId, debt) }

          //when
          val t: Task[List[PutItemResponse]] =
            Observable
              .fromIterable(requests)
              .transform(transformer)
              .toListL
              .map(Task.sequence(_))
              .flatten

          //then
          whenReady(t.runToFuture) { r =>
            r shouldBe a[List[PutItemResponse]]
            requestAttr.map { case (city, citizenId, debt) =>
              val getResponse: GetItemResponse = toScala(client.getItem(getItemRequest(tableName, city, citizenId))).futureValue
              getResponse.item().values().asScala.head.n().toDouble shouldBe debt
            }
          }
        }
      }

    s"given an implicit instance of ${DynamoDbOp.Implicits.getItemOp} in the scope" must {

      s"transforms a single `GetItemRequest` to `GetItemResponse` " in {
        //given
        val city = "London"
        val citizenId = 613371
        val debt: Int = 550
        toScala(client.putItem(putItemRequest(tableName, city, citizenId, debt))).futureValue
        val request: GetItemRequest = getItemRequest(tableName, city, citizenId)
        val transformer: Transformer[GetItemRequest, Task[GetItemResponse]] = DynamoDb.transformer()

        //when
        val t: Task[GetItemResponse] =
          Observable.fromIterable(Iterable(request)).transform(transformer).headL.runSyncUnsafe()

        //then
        whenReady(t.runToFuture) { response =>
          response shouldBe a[GetItemResponse]
          response.hasItem shouldBe true
          response.item() should contain key "debt"
          response.item().values().asScala.head.n().toDouble shouldBe debt
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    createTable(tableName)
    sleep(2000)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
