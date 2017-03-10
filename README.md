CloudFormation Config Rule
==========================

The aim of this config rule is to report resource compliance based on whether the resource is owned by a CloudFormation 
stack.

The config rule is written in Scala.

### TODO

 - There is only a low level of coverage of resource types thus far, more resource types need to be added.
 - Improve CFN template to include the definition of the config rule itself
 - Add build and installation instructions to README