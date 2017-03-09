

name := "cfn-config-rule"
organization  := "com.gu"

scalaVersion in ThisBuild := "2.12.1"

description   := "Config rule lambda for checking resources are related to cloudformation stacks"
scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

val awsVersion = "1.11.99"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.amazonaws" % "aws-lambda-java-events" % "1.3.0" intransitive(),
  "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.21",
  "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-iam" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-cloudformation" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-autoscaling" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-lambda" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-config" % awsVersion,
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)


publishMavenStyle := false

enablePlugins(JavaAppPackaging)

topLevelDirectory in Universal := None
packageName in Universal := normalizedName.value