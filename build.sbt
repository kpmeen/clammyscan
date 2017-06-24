/*

    Build script for ClammyScan, a reactive integration with ClamAV.

 */

name := """clammyscan"""

organization := "net.scalytica"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

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

scalaVersion := "2.11.11"

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
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

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.jcenterRepo
)

val playVersion      = "2.6.0"
val akkaVersion      = "2.5.3"
val stestVersion     = "3.0.1"
val stestPlusVersion = "3.0.0"

libraryDependencies ++= Seq(
  // Play!
  "com.typesafe.play" %% "play"       % playVersion % Provided,
  "com.typesafe.play" %% "play-guice" % playVersion % Provided,
  "com.typesafe.play" %% "play-test"  % playVersion % Test,
  // Akka
  "com.typesafe.akka" %% "akka-actor"   % akkaVersion % Provided,
  "com.typesafe.akka" %% "akka-stream"  % akkaVersion % Provided,
  "com.typesafe.akka" %% "akka-slf4j"   % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  // ScalaTest
  "org.scalactic"          %% "scalactic"          % stestVersion     % Test,
  "org.scalatest"          %% "scalatest"          % stestVersion     % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % stestPlusVersion % Test
)
