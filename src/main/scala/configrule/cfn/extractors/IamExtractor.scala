package configrule.cfn.extractors

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model._

import scala.collection.JavaConverters._

class IamExtractor(val client: AmazonIdentityManagement) extends ResourceServiceExtractor[AmazonIdentityManagement] {
  override def resourceTypes = List(iamInstanceProfileResourceType, iamPolicyResourceType, iamRoleResourceType, iamUserResourceType)

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
}
