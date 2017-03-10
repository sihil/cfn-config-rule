package configrule.cfn.extractors

trait ResourceType[T] {
  def awsType: String
  def name(t: T): String
  def fetchAll: List[T]
  /** is a particular instance not applicable */
  def notApplicable: Set[String] = Set.empty
  def isNotApplicable(t: T) = notApplicable.contains(name(t))
}