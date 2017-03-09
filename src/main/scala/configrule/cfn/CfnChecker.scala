package configrule.cfn

import java.time.ZonedDateTime

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.{ListStackResourcesRequest, ListStacksRequest, StackStatus, StackSummary}
import com.amazonaws.services.config.model.{Evaluation, PutEvaluationsRequest}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.ConfigEvent
import configrule.cfn.extractors._

import scala.collection.JavaConverters._

object CfnChecker {
  val usefulStackStates = List(
    StackStatus.CREATE_COMPLETE,
    StackStatus.UPDATE_COMPLETE,
    StackStatus.UPDATE_COMPLETE_CLEANUP_IN_PROGRESS,
    StackStatus.UPDATE_IN_PROGRESS,
    StackStatus.UPDATE_ROLLBACK_COMPLETE,
    StackStatus.UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS,
    StackStatus.UPDATE_ROLLBACK_FAILED,
    StackStatus.UPDATE_ROLLBACK_IN_PROGRESS,
    StackStatus.DELETE_FAILED,
    StackStatus.ROLLBACK_COMPLETE,
    StackStatus.ROLLBACK_FAILED,
    StackStatus.ROLLBACK_IN_PROGRESS
  )

  def main(args: Array[String]): Unit = {
    val checker = new CfnChecker(new ProfileCredentialsProvider("composer-iam"), testMode = true)
    checker.doEvaluations(new ConfigEvent(), checker.extractors)
  }
}

case class Resource(stack: String, name: String, awsType: String)

class CfnChecker(creds: AWSCredentialsProvider, testMode: Boolean) extends Logging {

  def this() {
    this(new DefaultAWSCredentialsProviderChain(), testMode = false)
  }

  implicit val credentialsProvider = creds
  implicit val region = AWSClientFactory.getRegion
  implicit val configClient = AWSClientFactory.createConfigClient
  implicit val cfnClient = AWSClientFactory.createCfnClient


  val extractors = {
    val asgClient = AWSClientFactory.createAsgClient
    val ec2Client = AWSClientFactory.createEc2Client
    val elbClient = AWSClientFactory.createElbClient
    val iamClient = AWSClientFactory.createIamClient
    val dynamoClient = AWSClientFactory.createDynamoClient
    List(
      new IamExtractor(iamClient),
      new DynamoExtractor(dynamoClient),
      new Ec2Extractor(ec2Client),
      new ElbExtractor(elbClient),
      new AutoScalingExtractor(asgClient)
    )
  }

  def run(event: ConfigEvent, context: Context): Unit = {
    doEvaluations(event, extractors)
  }

  trait ResourceType[T, C] {
    def awsType: String
    def name(t: T): String
    def fetchAll(client: C): List[T]
  }

  def doEvaluations(configEvent: ConfigEvent, extractors: List[ResourceServiceExtractor[_]]) = {
    log.info("Retrieving CFN stacks")
    val allResources = getStacks(cfnClient).filterNot(_.getStackStatus.startsWith("DELETE_")).flatMap{ summary =>
      getStackResources(cfnClient, summary)
    }
    log.info(s"Evaluating against ${allResources.size} CFN resources")

    val evaluations = extractors.flatMap(_.evaluate(allResources, ZonedDateTime.now()))

    putEvaluations(configEvent, evaluations)
  }

  def putEvaluations(configEvent: ConfigEvent, evaluations: List[Evaluation]) = {
    evaluations.grouped(100).foreach { evaluationGroup =>
      val request = new PutEvaluationsRequest()
        .withEvaluations(evaluationGroup.asJava)
        .withTestMode(testMode)
        .withResultToken(Option(configEvent.getResultToken).getOrElse(""))
      log.info(s"Putting ${evaluationGroup.size} evaluations")
      log.debug(request.toString)
      configClient.putEvaluations(
        request
      )
    }
  }

  def getStacks(awsCfn: AmazonCloudFormation): List[StackSummary] = resourceList { token =>
    val results = awsCfn.listStacks(new ListStacksRequest().withStackStatusFilters(CfnChecker.usefulStackStates: _*).withNextToken(token.orNull))
    results.getStackSummaries.asScala.toList -> Option(results.getNextToken)
  }

  def getStackResources(awsCfn: AmazonCloudFormation, stack: StackSummary) = resourceList { token =>
    val results = awsCfn.listStackResources(new ListStackResourcesRequest().withNextToken(token.orNull).withStackName(stack.getStackId))
    (
      results.getStackResourceSummaries.asScala.map{r =>
        Resource(stack.getStackName, r.getPhysicalResourceId, r.getResourceType)
      }.toList,
      Option(results.getNextToken)
    )
  }

}
