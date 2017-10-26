import Settings._
import Dependencies.{scalafmtVersion, _}
import org.scalafmt.bootstrap.ScalafmtBootstrap
import play.sbt.PlayImport
/*

    Build script for ClammyScan, a reactive integration with ClamAV.

 */

name := """clammyscan"""

// ============================================================================
//  Workaround for latest scalafmt in sbt 0.13.x
// ============================================================================
commands += Command.args("scalafmt", "Run scalafmt cli.") {
  case (s, args) =>
    val Right(scalafmt) = ScalafmtBootstrap.fromVersion(scalafmtVersion)
    scalafmt.main("--non-interactive" +: args.toArray)
    s
}

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
  .settings(routesGenerator := InjectedRoutesGenerator)
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
