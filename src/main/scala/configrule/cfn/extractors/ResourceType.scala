package configrule.cfn.extractors

trait ResourceType[T] {
  def awsType: String
  def name(t: T): String
  def fetchAll: List[T]
  def notApplicable: Set[String] = Set.empty
  def isNotApplicable(t: T) = notApplicable.contains(name(t))
}