name := """ClammyScanSample"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator

scalacOptions ++= Seq(
  "-feature",
  "-language:postfixOps",
  "-language:higherKinds"
)

libraryDependencies ++= Seq(
  cache,
  ws
)

libraryDependencies ++= Seq(
  "net.scalytica" %% "clammyscan" % "1.0.9"
)

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.jcenterRepo
)
