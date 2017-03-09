package configrule.cfn.extractors

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.{AutoScalingGroup, DescribeAutoScalingGroupsRequest, DescribeLaunchConfigurationsRequest, LaunchConfiguration}
import scala.collection.JavaConverters._

class AutoScalingExtractor(val client: AmazonAutoScaling) extends ResourceServiceExtractor[AmazonAutoScaling] {
  override def resourceTypes = List(autoScalingGroupResourceType, autoScalingLaunchConfigResourceType)
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

  def getAutoScalingGroups(asgClient: AmazonAutoScaling) = resourceList { token =>
    val results = asgClient.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withNextToken(token.orNull))
    results.getAutoScalingGroups.asScala.toList -> Option(results.getNextToken)
  }

  def getAutoScalingLaunchConfiguration(asgClient: AmazonAutoScaling) = resourceList { token =>
    val results = asgClient.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withNextToken(token.orNull))
    results.getLaunchConfigurations.asScala.toList -> Option(results.getNextToken)
  }
}
