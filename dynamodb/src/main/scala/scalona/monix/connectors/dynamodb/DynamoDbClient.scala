package scalona.monix.connectors.dynamodb

import java.net.URI

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.regions.Region

object DynamoDbClient {
  def apply(): DynamoDbAsyncClient = {
    val config: DynamoDbConfig = DynamoAppConfig.load()
    DynamoDbAsyncClient.builder()
      .credentialsProvider(config.awsCredProvider)
      .endpointOverride(new URI(config.endPoint))
      .region(Region.AWS_GLOBAL)
      .build()
  }
}