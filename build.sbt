import Settings._
import Dependencies._
import play.sbt.PlayImport
import play.sbt.routes.RoutesKeys
/*

    Build script for ClammyScan, a reactive integration with ClamAV.

 */

name := """clammyscan"""

lazy val root =
  (project in file(".")).settings(NoPublish).aggregate(library, sample)

lazy val library = ClammyProject("clammyscan", Some("library"))
  .settings(
    coverageMinimum := 80,
    coverageFailOnMinimum := true
  )
  .settings(libraryDependencies ++= PlayDeps ++ AkkaDeps ++ ScalaTestDeps)
  .settings(BintrayPublish: _*)

lazy val sample = ClammyProject("sample")
  .enablePlugins(PlayScala)
  .settings(
    Seq(
      routesGenerator := InjectedRoutesGenerator,
      RoutesKeys.routesImport := Seq.empty
    )
  )
  .settings(coverageEnabled := false)
  .settings(resolvers += Resolver.mavenLocal)
  .settings(NoPublish)
  .settings(
    libraryDependencies ++= Seq(
      PlayImport.guice,
      PlayImport.ws,
      PlayImport.ehcache,
      "com.typesafe.play" %% "play-json" % playVersion
    )
  )
  .dependsOn(library)
