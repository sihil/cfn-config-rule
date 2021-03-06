package configrule.cfn

import org.slf4j.LoggerFactory

trait Logging {
  lazy val log = LoggerFactory.getLogger(this.getClass)
}
