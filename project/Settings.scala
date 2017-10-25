import sbt.Keys._
import sbt._

// scalastyle:off
object Settings {

  val ScalacOpts = Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-override",
    "-Ywarn-numeric-widen",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps"
  )

  val BaseSettings = Seq(
    scalaVersion := "2.11.11",
    scalacOptions := ScalacOpts,
    organization := "net.scalytica",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    logBuffered in Test := false,
    javaOptions in Test += "-Dlogger.resource=logback-test.xml",
    testOptions += Tests
      .Argument(TestFrameworks.Specs2, "html", "junitxml", "console")
  )

  val NoPublish = Seq(
    publish := {},
    publishLocal := {}
  )

  val BintrayPublish = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomExtra := (
      <url>https://github.com/kpmeen/clammyscan</url>
        <scm>
          <url>git@github.com:kpmeen/clammyscan.git</url>
          <connection>scm:git:git@github.com:kpmeen/clammyscan.git</connection>
        </scm>
        <developers>
          <developer>
            <id>kpmeen</id>
            <name>Knut Petter Meen</name>
            <url>http://scalytica.net</url>
          </developer>
        </developers>
    )
  )

  def ClammyProject(name: String, folder: Option[String] = None): Project = {
    val folderName = folder.getOrElse(name)
    Project(name, file(folderName))
      .settings(BaseSettings: _*)
      .settings(
        updateOptions := updateOptions.value.withCachedResolution(true)
      )
      .settings(resolvers ++= Dependencies.Resolvers)
  }

}
// scalastyle:on
