package configrule.cfn

package object extractors {
  def resourceList[T](getResources: Option[String] => (List[T], Option[String])): List[T] = {
    def rec(nextToken: Option[String]): List[T] = {
      val (resources, token) = getResources(nextToken)
      if (token.isEmpty) {
        resources
      } else {
        resources ::: rec(token)
      }
    }
    rec(None)
  }
}
