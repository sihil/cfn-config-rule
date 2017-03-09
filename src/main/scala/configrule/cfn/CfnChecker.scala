package configrule.cfn

import java.time.ZonedDateTime
import java.util.Date

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.{AutoScalingGroup, DescribeAutoScalingGroupsRequest, DescribeLaunchConfigurationsRequest, LaunchConfiguration}
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.{ListStackResourcesRequest, ListStacksRequest, StackStatus, StackSummary}
import com.amazonaws.services.config.model.{ComplianceType, Evaluation, PutEvaluationsRequest}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.ListTablesRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeSecurityGroupsRequest, SecurityGroup}
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, LoadBalancerDescription}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.ConfigEvent

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
    checker.doEvaluations(new ConfigEvent())
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
  implicit val asgClient = AWSClientFactory.createAsgClient
  implicit val ec2Client = AWSClientFactory.createEc2Client
  implicit val elbClient = AWSClientFactory.createElbClient
  implicit val iamClient = AWSClientFactory.createIamClient
  implicit val dynamoClient = AWSClientFactory.createDynamoClient

  def run(event: ConfigEvent, context: Context): Unit = {
    doEvaluations(event)
  }

  trait ResourceType[T, C] {
    def awsType: String
    def name(t: T): String
    def fetchAll(client: C): List[T]
  }

  val iamPolicyResourceType = new ResourceType[Policy, AmazonIdentityManagement] {
    override def awsType = "AWS::IAM::Policy"
    override def name(t: Policy) = t.getPolicyName
    override def fetchAll(client: AmazonIdentityManagement) = getIamPolicies(client)
  }

  val iamRoleResourceType = new ResourceType[Role, AmazonIdentityManagement] {
    override def awsType = "AWS::IAM::Role"
    override def name(r: Role) = r.getRoleName
    override def fetchAll(client: AmazonIdentityManagement) = getIamRoles(client)
  }

  val iamUserResourceType = new ResourceType[User, AmazonIdentityManagement] {
    override def awsType = "AWS::IAM::User"
    override def name(u: User) = u.getUserName
    override def fetchAll(client: AmazonIdentityManagement) = getIamUsers(client)
  }

  val iamInstanceProfileResourceType = new ResourceType[InstanceProfile, AmazonIdentityManagement] {
    override def awsType = "AWS::IAM::InstanceProfile"
    override def name(ip: InstanceProfile) = ip.getInstanceProfileName
    override def fetchAll(client: AmazonIdentityManagement) = getIamInstanceProfiles(client)
  }

  val securityGroupResourceType = new ResourceType[SecurityGroup, AmazonEC2] {
    override def awsType = "AWS::EC2::SecurityGroup"
    override def name(t: SecurityGroup) = t.getGroupId
    override def fetchAll(client: AmazonEC2) = getEc2SecurityGroups(client)
  }

  val loadBalancerResourceType = new ResourceType[LoadBalancerDescription, AmazonElasticLoadBalancing] {
    override def awsType = "AWS::ElasticLoadBalancing::LoadBalancer"
    override def name(lb: LoadBalancerDescription) = lb.getLoadBalancerName
    override def fetchAll(client: AmazonElasticLoadBalancing) = getElbLoadBalancers(client)
  }

  val dynamoDbTableResourceType = new ResourceType[String, AmazonDynamoDB] {
    override def awsType = "AWS::DynamoDB::Table"
    override def name(name: String) = name
    override def fetchAll(client: AmazonDynamoDB) = getDynamoTables(client)
  }

  val autoScalingGroupResourceType = new ResourceType[AutoScalingGroup, AmazonAutoScaling] {
    override def awsType = "AWS::AutoScaling::AutoScalingGroup"
    override def name(t: AutoScalingGroup) = t.getAutoScalingGroupName
    override def fetchAll(client: AmazonAutoScaling) = getAutoScalingGroups(client)
  }

  val autoScalingLaunchConfigResourceType = new ResourceType[LaunchConfiguration, AmazonAutoScaling] {
    override def awsType = "AWS::AutoScaling::LaunchConfiguration"
    override def name(lc: LaunchConfiguration) = lc.getLaunchConfigurationName
    override def fetchAll(client: AmazonAutoScaling) = getAutoScalingLaunchConfiguration(client)
  }

  val resourceTypes = List(iamPolicyResourceType, iamRoleResourceType, iamUserResourceType, securityGroupResourceType, autoScalingGroupResourceType)

  def evaluate[T, C](resourceType: ResourceType[T, C], stackResources: List[Resource], date: ZonedDateTime)
                       (implicit client: C): List[Evaluation] = {
    val resources = resourceType.fetchAll(client)
    log.info(s"Examining ${resources.length} ${resourceType.awsType}")
    val oldSchoolDate = Date.from(date.toInstant)
    val cfnResourceNames = stackResources.filter(_.awsType == resourceType.awsType).map(_.name).toSet
    log.debug(s"All resource names: $cfnResourceNames")
    val evaluations = resources.map{ r =>
      val complianceType = if (cfnResourceNames.contains(resourceType.name(r))) {
        ComplianceType.COMPLIANT
      } else {
        log.debug(s"${resourceType.name(r)} not compliant")
        ComplianceType.NON_COMPLIANT
      }
      new Evaluation()
        .withComplianceResourceType(resourceType.awsType)
        .withComplianceResourceId(resourceType.name(r))
        .withComplianceType(complianceType)
        .withOrderingTimestamp(oldSchoolDate)
    }
    evaluations.groupBy(_.getComplianceType).toList.sortBy(_._1).foreach{ case (complianceType, incidences) =>
        log.info(s"  $complianceType: ${incidences.size}")
    }
    evaluations
  }

  def doEvaluations(configEvent: ConfigEvent) = {
    log.info("Retrieving CFN stacks")
    val allResources = getStacks(cfnClient).filterNot(_.getStackStatus.startsWith("DELETE_")).flatMap{ summary =>
      getStackResources(cfnClient, summary)
    }

    val dateTime = ZonedDateTime.now()

    val evaluations =
      evaluate(iamPolicyResourceType, allResources, dateTime) :::
      evaluate(iamRoleResourceType, allResources, dateTime) :::
      evaluate(iamUserResourceType, allResources, dateTime) :::
      evaluate(iamInstanceProfileResourceType, allResources, dateTime) :::
      evaluate(dynamoDbTableResourceType, allResources, dateTime) :::
      evaluate(securityGroupResourceType, allResources, dateTime) :::
      evaluate(loadBalancerResourceType, allResources, dateTime) :::
      evaluate(autoScalingGroupResourceType, allResources, dateTime) :::
      evaluate(autoScalingLaunchConfigResourceType, allResources, dateTime)

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

  def resourceList[T](getResources: Option[String] => (List[T], Option[String])): List[T] = {
    def rec(nextToken: Option[String]): List[T] = {
      val (resources, token) = getResources(nextToken)
      if (token.isEmpty) {
        resources
      } else {
        resources ::: rec(token)
      }
    }
    rec(None)
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

  def getIamPolicies(iamClient: AmazonIdentityManagement) = resourceList { token =>
    val result = iamClient.listPolicies(new ListPoliciesRequest().withMarker(token.orNull).withScope(PolicyScopeType.Local))
    result.getPolicies.asScala.toList -> Option(result.getMarker)
  }

  def getIamRoles(iamClient: AmazonIdentityManagement) = resourceList { token =>
    val result = iamClient.listRoles(new ListRolesRequest().withMarker(token.orNull))
    result.getRoles.asScala.toList -> Option(result.getMarker)
  }

  def getIamUsers(iamClient: AmazonIdentityManagement) = resourceList { token =>
    val result = iamClient.listUsers(new ListUsersRequest().withMarker(token.orNull))
    result.getUsers.asScala.toList -> Option(result.getMarker)
  }

  def getIamInstanceProfiles(iamClient: AmazonIdentityManagement) = resourceList { token =>
    val result = iamClient.listInstanceProfiles(new ListInstanceProfilesRequest().withMarker(token.orNull))
    result.getInstanceProfiles.asScala.toList -> Option(result.getMarker)
  }

  def getAutoScalingGroups(asgClient: AmazonAutoScaling) = resourceList { token =>
    val results = asgClient.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withNextToken(token.orNull))
    results.getAutoScalingGroups.asScala.toList -> Option(results.getNextToken)
  }

  def getAutoScalingLaunchConfiguration(asgClient: AmazonAutoScaling) = resourceList { token =>
    val results = asgClient.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withNextToken(token.orNull))
    results.getLaunchConfigurations.asScala.toList -> Option(results.getNextToken)
  }

  def getEc2SecurityGroups(ec2Client: AmazonEC2): List[SecurityGroup] =
    ec2Client.describeSecurityGroups(new DescribeSecurityGroupsRequest()).getSecurityGroups.asScala.toList

  def getElbLoadBalancers(elbClient: AmazonElasticLoadBalancing): List[LoadBalancerDescription] = resourceList { marker =>
    val results = elbClient.describeLoadBalancers(new DescribeLoadBalancersRequest().withMarker(marker.orNull))
    results.getLoadBalancerDescriptions.asScala.toList -> Option(results.getNextMarker)
  }

  def getDynamoTables(dynamoClient: AmazonDynamoDB): List[String] = resourceList { startTableName =>
    val results = dynamoClient.listTables(new ListTablesRequest().withExclusiveStartTableName(startTableName.orNull))
    results.getTableNames.asScala.toList -> Option(results.getLastEvaluatedTableName)
  }

}
