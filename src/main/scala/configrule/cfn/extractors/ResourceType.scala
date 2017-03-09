package configrule.cfn.extractors

trait ResourceType[T, C] {
  def awsType: String
  def name(t: T): String
  def fetchAll(client: C): List[T]
}