package configrule.cfn.extractors

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.ListTablesRequest
import scala.collection.JavaConverters._

class DynamoExtractor(client: AmazonDynamoDB) extends ResourceServiceExtractor {
  override def resourceTypes = List(dynamoDbTableResourceType)

  val dynamoDbTableResourceType = new ResourceType[String] {
    override def awsType = "AWS::DynamoDB::Table"
    override def name(name: String) = name
    override def fetchAll = getDynamoTables(client)
  }

  def getDynamoTables(dynamoClient: AmazonDynamoDB): List[String] = resourceList { startTableName =>
    val results = dynamoClient.listTables(new ListTablesRequest().withExclusiveStartTableName(startTableName.orNull))
    results.getTableNames.asScala.toList -> Option(results.getLastEvaluatedTableName)
  }

}
