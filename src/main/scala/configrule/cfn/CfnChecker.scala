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
  // list of CFN stack states that we should ignore (deleted or create_failed)
  val ignoreStackStates = List(
    StackStatus.CREATE_FAILED,
    StackStatus.CREATE_IN_PROGRESS,
    StackStatus.DELETE_COMPLETE,
    StackStatus.DELETE_IN_PROGRESS
  )
  // we need a positive list of state to include
  val usefulStackStates = StackStatus.values().toList.filterNot(ignoreStackStates.contains)

  def main(args: Array[String]): Unit = {
    val checker = new CfnChecker(new ProfileCredentialsProvider("composer-iam"), testMode = true)
    checker.doEvaluations(new ConfigEvent(), checker.extractors)
  }
}

case class Resource(stack: String, name: String, awsType: String)

class CfnChecker(creds: AWSCredentialsProvider, testMode: Boolean) extends Logging {

  // no-arg constructor for AWS Lambda
  def this() {
    this(new DefaultAWSCredentialsProviderChain(), testMode = false)
  }

  implicit val credentialsProvider = creds
  implicit val region = AWSClientFactory.getRegion
  implicit val configClient = AWSClientFactory.createConfigClient
  implicit val cfnClient = AWSClientFactory.createCfnClient

  private val autoScalingExtractor = new AutoScalingExtractor(AWSClientFactory.createAsgClient)
  val extractors = List(
    new IamExtractor(AWSClientFactory.createIamClient),
    new DynamoExtractor(AWSClientFactory.createDynamoClient),
    new Ec2Extractor(AWSClientFactory.createEc2Client, autoScalingExtractor),
    new ElbExtractor(AWSClientFactory.createElbClient),
    autoScalingExtractor
  )

  // AWS Lambda handler entrypoint
  def run(event: ConfigEvent, context: Context): Unit = {
    doEvaluations(event, extractors)
  }

  def doEvaluations(configEvent: ConfigEvent, extractors: List[ResourceServiceExtractor]) = {
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
