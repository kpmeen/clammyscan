name := """ClammyScanSample"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:higherKinds")

libraryDependencies ++= Seq(
  cache,
  ws
)

val logbackVersion = "1.1.7"
val slf4jVersion = "1.7.21"

libraryDependencies ++= Seq(
  "net.scalytica" %% "clammyscan" % "1.0.5",
  // Logging
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)
