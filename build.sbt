import Settings._
import Dependencies._
import play.sbt.PlayImport
import play.sbt.routes.RoutesKeys

import scala.sys.process._

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

lazy val root =
  (project in file("."))
    .settings(NoPublish)
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
  .settings(BintrayPublish: _*)
  .dependsOn(sharedTesting % Test)

lazy val bodyParsers = ClammyProject("clammyscan", Some("bodyparsers"))
  .settings(
    coverageMinimum := 75,
    coverageFailOnMinimum := true
  )
  .settings(
    libraryDependencies ++= PlayDeps.All ++ AkkaDeps.All ++ TestingDeps.All
  )
  .settings(BintrayPublish: _*)
  .dependsOn(streamsLib, sharedTesting % Test)

lazy val sample = ClammyProject("sample")
  .enablePlugins(PlayScala)
  .settings(
    routesGenerator := InjectedRoutesGenerator,
    RoutesKeys.routesImport := Seq.empty
  )
  .settings(coverageEnabled := false)
  .settings(resolvers += Resolver.mavenLocal)
  .settings(NoPublish)
  .settings(
    libraryDependencies ++= Seq(
      PlayImport.guice,
      PlayImport.ws,
      PlayImport.ehcache,
      PlayDeps.PlayJson
    )
  )
  .dependsOn(bodyParsers)
