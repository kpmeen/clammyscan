name := """clammyscan"""

version := "0.22-SNAPSHOT"

organization := "net.scalytica"

licenses +=("MIT", url("http://opensource.org/licenses/MIT"))

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

scalacOptions ++= Seq("-deprecation", "-feature", "-language:higherKinds")
scalacOptions in Test ++= Seq("-Yrangepos")

scalaVersion := "2.11.7"

publishArtifact in Test := false

bintraySettings

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype" at "http://oss.sonatype.org/content/groups/public/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.4.2" % "provided",
  "com.typesafe.play" %% "play-test" % "2.4.2" % "test",
  "org.specs2" %% "specs2-core" % "3.6.2" % "test"
)
