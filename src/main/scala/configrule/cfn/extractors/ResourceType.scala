package configrule.cfn.extractors

trait ResourceType[T] {
  def awsType: String
  def name(t: T): String
  def fetchAll: List[T]
}