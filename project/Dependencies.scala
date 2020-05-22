import sbt._

// scalastyle:off
object Dependencies {

  val Resolvers = Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("releases"),
    Resolver.jcenterRepo
  )

  val playVersion      = play.core.PlayVersion.current
  val playJsonVersion  = "2.8.2"
  val akkaVersion      = "2.6.5"
  val slf4jVersion     = "1.7.30"
  val logbackVersion   = "1.2.3"
  val stestVersion     = "3.1.2"
  val stestPlusVersion = "5.1.0"

  object PlayDeps {

    val All = Seq(
      "com.typesafe.play" %% "play"                  % playVersion % Provided,
      "com.typesafe.play" %% "play-guice"            % playVersion % Provided,
      "com.typesafe.play" %% "play-logback"          % playVersion % Provided,
      "com.typesafe.play" %% "play-akka-http-server" % playVersion % Test,
      "com.typesafe.play" %% "play-test"             % playVersion % Test
    )

    val PlayJson = "com.typesafe.play" %% "play-json" % playJsonVersion
  }

  object AkkaDeps {
    val actor  = "com.typesafe.akka" %% "akka-actor"  % akkaVersion % Provided
    val stream = "com.typesafe.akka" %% "akka-stream" % akkaVersion % Provided

    val slf4j         = "com.typesafe.akka" %% "akka-slf4j"          % akkaVersion
    val actorTestkit  = "com.typesafe.akka" %% "akka-testkit"        % akkaVersion
    val streamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion

    val All = Seq(
      actor,
      stream,
      slf4j         % Test,
      actorTestkit  % Test,
      streamTestkit % Test
    )
  }

  object LoggingDeps {
    val Slf4jApi = "org.slf4j"      % "slf4j-api"       % slf4jVersion
    val Slf4jNop = "org.slf4j"      % "slf4j-nop"       % slf4jVersion
    val Logback  = "ch.qos.logback" % "logback-classic" % logbackVersion
  }

  object TestingDeps {
    val Scalactic     = "org.scalactic"          %% "scalactic"          % stestVersion
    val ScalaTest     = "org.scalatest"          %% "scalatest"          % stestVersion
    val ScalaTestPlay = "org.scalatestplus.play" %% "scalatestplus-play" % stestPlusVersion

    val AllNoPlay = Seq(Scalactic % Test, ScalaTest % Test)

    val All =
      Seq(Scalactic % Test, ScalaTest % Test, ScalaTestPlay % Test)
  }

  object Overrides {

    val overrides = Seq(
      "com.typesafe.akka" %% "akka-discovery"           % akkaVersion,
      "com.typesafe.akka" %% "akka-protobuf"            % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-actor"               % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"               % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit"      % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed"        % akkaVersion
    )
  }

}
