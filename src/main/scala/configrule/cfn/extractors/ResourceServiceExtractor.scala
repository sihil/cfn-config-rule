package configrule.cfn.extractors

import java.time.ZonedDateTime
import java.util.Date

import com.amazonaws.services.config.model.{ComplianceType, Evaluation}
import configrule.cfn.{Logging, Resource}

trait ResourceServiceExtractor extends Logging {
  def evaluate(cfnResources: List[Resource], dateTime: ZonedDateTime): List[Evaluation] = {
    resourceTypes.flatMap { resourceType =>
      evaluate(resourceType, cfnResources, dateTime)
    }
  }
  def evaluate[T](resourceType: ResourceType[T], stackResources: List[Resource], date: ZonedDateTime): List[Evaluation] = {
    val resources = resourceType.fetchAll
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
  def resourceTypes: List[ResourceType[_]]
}

