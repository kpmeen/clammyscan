import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

name := """clammyscan"""

organization := "net.scalytica"

licenses +=("MIT", url("http://opensource.org/licenses/MIT"))

pomExtra := (
  <url>https://github.com/kpmeen/clammyscan</url>
  <scm>
    <url>git@github.com:kpmeen/clammyscan.git</url>
    <connection>scm:git:git@github.com:kpmeen/clammyscan.git</connection>
  </scm>
  <developers>
    <developer>
      <id>kpmeen</id>
      <name>Knut Petter Meen</name>
      <url>http://scalytica.net</url>
    </developer>
  </developers>
  )

scalaVersion := "2.11.8"

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

publishMavenStyle := true
publishArtifact in Test := false

coverageMinimum := 80
coverageFailOnMinimum := true

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(FormatXml, false)
  .setPreference(DoubleIndentClassDeclaration, false)
  .setPreference(SpacesAroundMultiImports, false)

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.jcenterRepo
)

val playVersion = "2.5.9"
val akkaVersion = "2.4.11"
val stestVersion = "2.2.6"
val stestPlusVersion = "1.5.0"

libraryDependencies ++= Seq(
  // Play!
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-test" % playVersion % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion % "provided",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion % "provided",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  // ScalaTest
  "org.scalactic" %% "scalactic" % stestVersion % "test",
  "org.scalatest" %% "scalatest" % stestVersion % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % stestPlusVersion % "test"
)
