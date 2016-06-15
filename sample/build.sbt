name := """ClammyScanSample"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:higherKinds")

javaOptions in Test ++= Seq("-Dlogger.file=test/resources/logback-test.xml")

libraryDependencies ++= Seq(
  cache,
  ws
)

libraryDependencies ++= Seq(
  "net.scalytica" %% "clammyscan" % "0.23-SNAPSHOT"
)

resolvers ++= Seq(
  Resolver.defaultLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("scalaz", "releases")
)
