import play.PlayImport._

name := """ClammyScanSample"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

routesGenerator := InjectedRoutesGenerator

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:higherKinds")

javaOptions in Test ++= Seq("-Dlogger.file=test/resources/logback-test.xml")

libraryDependencies ++= Seq(
  cache,
  ws
)

libraryDependencies ++= Seq(
  "net.scalytica" %% "clammyscan" % "0.23-SNAPSHOT",
  "org.codehaus.janino" % "janino" % "2.7.6",
  "org.specs2" %% "specs2-core" % "3.6.2" % "test"
)

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  "embedded mongo repository" at "http://oss.sonatype.org/content/repositories/releases",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)
