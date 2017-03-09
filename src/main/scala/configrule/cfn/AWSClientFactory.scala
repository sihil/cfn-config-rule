package configrule.cfn

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.retry.{PredefinedRetryPolicies, RetryPolicy}
import com.amazonaws.services.autoscaling.{AmazonAutoScaling, AmazonAutoScalingClientBuilder}
import com.amazonaws.services.cloudformation.{AmazonCloudFormation, AmazonCloudFormationClientBuilder}
import com.amazonaws.services.cloudwatch.{AmazonCloudWatch, AmazonCloudWatchClientBuilder}
import com.amazonaws.services.config.{AmazonConfig, AmazonConfigClientBuilder}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.amazonaws.services.elasticloadbalancing.{AmazonElasticLoadBalancing, AmazonElasticLoadBalancingClientBuilder}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagement, AmazonIdentityManagementClientBuilder}
import com.amazonaws.services.lambda.{AWSLambda, AWSLambdaClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sns.{AmazonSNS, AmazonSNSClientBuilder}

object AWSClientFactory {


  def getRegion = Option(System.getenv("AWS_DEFAULT_REGION")).map(Regions.fromName).get

  val clientConfig = new ClientConfiguration().withRetryPolicy(
    new RetryPolicy(
      PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION,
      PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
      20,
      false
    )
  )

  def createSNSClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonSNS =
    AmazonSNSClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createS3Client(implicit region:Regions, creds: AWSCredentialsProvider): AmazonS3 =
    AmazonS3ClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createLambdaClient(implicit region:Regions, creds: AWSCredentialsProvider): AWSLambda =
    AWSLambdaClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createCloudwatchClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonCloudWatch =
    AmazonCloudWatchClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createConfigClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonConfig =
    AmazonConfigClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createCfnClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonCloudFormation =
    AmazonCloudFormationClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createAsgClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonAutoScaling =
    AmazonAutoScalingClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createEc2Client(implicit region:Regions, creds: AWSCredentialsProvider): AmazonEC2 =
    AmazonEC2ClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createElbClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonElasticLoadBalancing =
    AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createIamClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonIdentityManagement =
    AmazonIdentityManagementClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
  def createDynamoClient(implicit region:Regions, creds: AWSCredentialsProvider): AmazonDynamoDB =
    AmazonDynamoDBClientBuilder.standard().withRegion(region).withCredentials(creds).withClientConfiguration(clientConfig).build()
}