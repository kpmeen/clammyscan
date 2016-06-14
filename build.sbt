import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

name := """clammyscan"""

version := "0.23-SNAPSHOT"

organization := "net.scalytica"

licenses +=("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-language:postfixOps"
)

scalacOptions in Test ++= Seq("-Yrangepos")

scalaVersion := "2.11.8"

publishArtifact in Test := false

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(FormatXml, false)
  .setPreference(SpacesAroundMultiImports, false)
  .setPreference(DoubleIndentClassDeclaration, false)

coverageExcludedPackages := "<empty>;Messages.*;ClamCommands"

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.bintrayRepo("scalaz", "releases")
)

val playVersion = "2.5.3"
val akkaVersion = "2.4.4"
val specs2Version = "3.8.3"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-test" % playVersion % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.specs2" %% "specs2-core" % specs2Version % "test"
)