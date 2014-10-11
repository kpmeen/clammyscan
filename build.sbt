name := """clammyscan"""

version := "0.19-SNAPSHOT"

organization := "net.scalytica"

licenses +=("MIT", url("http://opensource.org/licenses/MIT"))

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions ++= Seq("-feature", "-language:higherKinds")

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.2")

publishArtifact in Test := false

bintraySettings

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype" at "http://oss.sonatype.org/content/groups/public/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.3.3" % "provided" cross CrossVersion.binary,
  "com.typesafe.play" %% "play-test" % "2.3.3" % "test" cross CrossVersion.binary,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23" % "provided" cross CrossVersion.binary,
  "org.specs2" %% "specs2" % "2.3.12" % "test" cross CrossVersion.binary
)

