package configrule.cfn.extractors

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersRequest, LoadBalancerDescription}
import scala.collection.JavaConverters._

class ElbExtractor(val client: AmazonElasticLoadBalancing) extends ResourceServiceExtractor[AmazonElasticLoadBalancing] {
  override def resourceTypes = List(loadBalancerResourceType)

  val loadBalancerResourceType = new ResourceType[LoadBalancerDescription, AmazonElasticLoadBalancing] {
    override def awsType = "AWS::ElasticLoadBalancing::LoadBalancer"
    override def name(lb: LoadBalancerDescription) = lb.getLoadBalancerName
    override def fetchAll(client: AmazonElasticLoadBalancing) = getElbLoadBalancers(client)
  }

  def getElbLoadBalancers(elbClient: AmazonElasticLoadBalancing): List[LoadBalancerDescription] = resourceList { marker =>
    val results = elbClient.describeLoadBalancers(new DescribeLoadBalancersRequest().withMarker(marker.orNull))
    results.getLoadBalancerDescriptions.asScala.toList -> Option(results.getNextMarker)
  }
}
