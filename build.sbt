import Settings._
import Dependencies._
import play.sbt.PlayImport
import play.sbt.routes.RoutesKeys

/*

    Build script for ClammyScan, a reactive integration with ClamAV.

 */

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
    coverageMinimum := 80,
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
