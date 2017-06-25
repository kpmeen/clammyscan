import sbt._

object Dependencies {

  val Resolvers = Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("releases"),
    Resolver.jcenterRepo
  )

  val playVersion      = "2.6.0"
  val akkaVersion      = "2.5.3"
  val stestVersion     = "3.0.1"
  val stestPlusVersion = "3.0.0"

  val PlayDeps = Seq(
    "com.typesafe.play" %% "play"       % playVersion % Provided,
    "com.typesafe.play" %% "play-guice" % playVersion % Provided,
    "com.typesafe.play" %% "play-test"  % playVersion % Test
  )

  val AkkaDeps = Seq(
    "com.typesafe.akka" %% "akka-actor"   % akkaVersion % Provided,
    "com.typesafe.akka" %% "akka-stream"  % akkaVersion % Provided,
    "com.typesafe.akka" %% "akka-slf4j"   % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
  )

  val ScalaTestDeps = Seq(
    "org.scalactic"          %% "scalactic"          % stestVersion     % Test,
    "org.scalatest"          %% "scalatest"          % stestVersion     % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % stestPlusVersion % Test
  )

}
