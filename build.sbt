import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

name := """clammyscan"""

version := "0.23-SNAPSHOT"

organization := "net.scalytica"

licenses +=("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  //  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
)

scalacOptions in Test ++= Seq("-Yrangepos")

scalaVersion := "2.11.8"

publishArtifact in Test := false

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(FormatXml, false)
  .setPreference(DoubleIndentClassDeclaration, false)
  .setPreference(SpacesAroundMultiImports, false)

coverageExcludedPackages := "<empty>;Messages.*;ClamCommands"

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("scalaz", "releases")
)

val playVersion = "2.5.4"
val akkaVersion = "2.4.4"
val logbackVersion = "1.1.7"
val slf4jVersion = "1.7.21"
val scalaTestVersion = "2.2.6"//"3.0.0-RC3"

libraryDependencies ++= Seq(
  // Play!
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-test" % playVersion % "test",
  // Ficus config DSL
  "com.iheart" %% "ficus" % "1.2.6",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  // Logging
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  // ScalaTest
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % "test"
)
