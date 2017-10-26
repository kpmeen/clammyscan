import sbt._

object Dependencies {

  val Resolvers = Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("releases"),
    Resolver.jcenterRepo
  )

  val playVersion      = play.core.PlayVersion.current
  val akkaVersion      = "2.5.6"
  val stestVersion     = "3.0.4"
  val stestPlusVersion = "3.1.2"
  val scalafmtVersion  = "1.3.0"

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
