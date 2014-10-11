import play.PlayImport._

name := """ClammyScanSample"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

scalacOptions ++= Seq("-feature", "-language:postfixOps", "-language:higherKinds")

javaOptions in Test ++= Seq("-Dlogger.file=test/resources/logback-test.xml")

libraryDependencies ++= Seq(
  cache,
  ws
)

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
  "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
  "net.scalytica" %% "clammyscan" % "0.18-SNAPSHOT",
  "org.codehaus.janino" % "janino" % "2.7.6",
  "org.specs2" %% "specs2" % "2.3.12" % "test"
)

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "JCenter" at "http://jcenter.bintray.com/",
  "embedded mongo repository" at "http://oss.sonatype.org/content/repositories/releases"
)
