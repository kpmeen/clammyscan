import Settings._
import Dependencies._
import play.sbt.PlayImport
import play.sbt.routes.RoutesKeys

import ReleaseTransformations._

import scala.sys.process._

// scalastyle:off
/*

    Build script for ClammyScan, a reactive integration with ClamAV.

 */

lazy val startClamd  = taskKey[Unit]("Start clamd container.")
lazy val stopClamd   = taskKey[Unit]("Stop clamd container.")
lazy val cleanClamd  = taskKey[Unit]("Clean clamd container.")
lazy val resetClamd  = taskKey[Unit]("Reset clamd container.")
lazy val statusClamd = taskKey[Unit]("Status clamd container.")

startClamd := { "./dockerClamAV.sh start" ! }
stopClamd := { "./dockerClamAV.sh stop" ! }
cleanClamd := { "./dockerClamAV.sh clean" ! }
resetClamd := { "./dockerClamAV.sh reset" ! }
statusClamd := { "./dockerClamAV.sh status" ! }

name := """clammyscan"""

releaseCrossBuild := false // See https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Note+about+sbt-release

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("+test"), // See https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Note+about+sbt-release
  setReleaseVersion,
  commitReleaseVersion, // performs the initial git checks
  tagRelease,
  releaseStepCommandAndRemaining("+publish"), // See https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Note+about+sbt-release
  setNextVersion,
  commitNextVersion,
  pushChanges // also checks that an upstream branch is properly configured
)

lazy val root = (project in file("."))
  .settings(NoPublish)
  .settings(crossScalaVersions := Nil)
  .aggregate(
    sharedTesting,
    streamsLib,
    bodyParsers,
    sample
  )

lazy val sharedTesting = ClammyProject("shared-testing")
  .settings(NoPublish)
  .settings(
    libraryDependencies ++= Seq(
      TestingDeps.ScalaTest,
      TestingDeps.Scalactic,
      AkkaDeps.actorTestkit,
      AkkaDeps.streamTestkit
    )
  )

lazy val streamsLib = ClammyProject("clammyscan-streams", Some("streams-lib"))
  .settings(
    coverageMinimum := 80,
    coverageFailOnMinimum := true
  )
  .settings(libraryDependencies ++= AkkaDeps.All ++ TestingDeps.AllNoPlay)
  .settings(
    libraryDependencies ++= Seq(
      LoggingDeps.Slf4jApi,
      LoggingDeps.Logback % Test
    )
  )
  .settings(BintrayPublish: _*)
  .dependsOn(sharedTesting % Test)

lazy val bodyParsers = ClammyProject("clammyscan", Some("bodyparsers"))
  .settings(
    coverageMinimum := 70,
    coverageFailOnMinimum := true
  )
  .settings(
    libraryDependencies ++= PlayDeps.All ++ AkkaDeps.All ++ TestingDeps.All
  )
  .settings(BintrayPublish: _*)
  .dependsOn(streamsLib, sharedTesting % Test)

lazy val sample = ClammyProject("sample")
  .enablePlugins(PlayScala)
  .settings(NoPublish)
  .settings(
    coverageEnabled := false,
    routesGenerator := InjectedRoutesGenerator,
    RoutesKeys.routesImport := Seq.empty
  )
  .settings(resolvers += Resolver.mavenLocal)
  .settings(
    libraryDependencies ++= Seq(
      PlayImport.akkaHttpServer,
      PlayImport.guice,
      PlayImport.ws,
      PlayImport.ehcache,
      PlayDeps.PlayJson
    )
  )
  .dependsOn(bodyParsers)
