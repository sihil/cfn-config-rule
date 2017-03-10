package configrule.cfn.extractors

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, DescribeSecurityGroupsRequest, Instance, SecurityGroup}

import scala.collection.JavaConverters._

class Ec2Extractor(client: AmazonEC2, asgExtractor: AutoScalingExtractor) extends ResourceServiceExtractor {
  override def resourceTypes = List(securityGroupResourceType, instanceResourceType)

  val securityGroupResourceType = new ResourceType[SecurityGroup] {
    override def awsType = "AWS::EC2::SecurityGroup"
    override def name(t: SecurityGroup) = t.getGroupId
    override def fetchAll = getEc2SecurityGroups(client)
  }

  val instanceResourceType = new ResourceType[Instance] {
    override def awsType = "AWS::EC2::Instance"
    override def name(t: Instance) = t.getInstanceId
    override def fetchAll = getInstances(client)
    override def notApplicable =
      asgExtractor.autoScalingGroupResourceType.fetchAll.flatMap(_.getInstances.asScala).map(_.getInstanceId).toSet
  }

  def getEc2SecurityGroups(ec2Client: AmazonEC2): List[SecurityGroup] =
    ec2Client.describeSecurityGroups(new DescribeSecurityGroupsRequest()).getSecurityGroups.asScala.toList

  def getInstances(ec2Client: AmazonEC2): List[Instance] = resourceList { token =>
    val results = ec2Client.describeInstances(new DescribeInstancesRequest().withNextToken(token.orNull))
    results.getReservations.asScala.toList.flatMap(_.getInstances.asScala) -> Option(results.getNextToken)
  }

}
