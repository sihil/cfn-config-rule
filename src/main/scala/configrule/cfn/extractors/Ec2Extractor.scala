package configrule.cfn.extractors

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{DescribeSecurityGroupsRequest, SecurityGroup}
import scala.collection.JavaConverters._

class Ec2Extractor(val client: AmazonEC2) extends ResourceServiceExtractor[AmazonEC2] {
  override def resourceTypes = List(securityGroupResourceType)

  val securityGroupResourceType = new ResourceType[SecurityGroup, AmazonEC2] {
    override def awsType = "AWS::EC2::SecurityGroup"
    override def name(t: SecurityGroup) = t.getGroupId
    override def fetchAll(client: AmazonEC2) = getEc2SecurityGroups(client)
  }

  def getEc2SecurityGroups(ec2Client: AmazonEC2): List[SecurityGroup] =
    ec2Client.describeSecurityGroups(new DescribeSecurityGroupsRequest()).getSecurityGroups.asScala.toList

}
