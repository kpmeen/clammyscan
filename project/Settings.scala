import sbt.Keys._
import sbt._

// scalastyle:off
object Settings {

  val ScalacOpts = Seq(
    "-encoding",
    "utf-8",         // Specify character encoding used by source files.
    "-feature",      // Emit warning and location for usages of features that should be imported explicitly.
    "-deprecation",  // Emit warning and location for usages of deprecated APIs.
    "-unchecked",    // Enable additional warnings where generated code depends on assumptions.
    "-explaintypes", // Explain type errors in more detail.
    "-Xcheckinit",   // Wrap field accessors to throw an exception on uninitialized access.
    // "-Xfatal-warnings",                 // Fail the compilation if there are any warnings.
    "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
    "-Xlint:doc-detached",           // A ScalaDoc comment appears to be detached from its element.
    "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",              // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",       // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",        // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-language:implicitConversions",
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    "-Ywarn-dead-code",        // Warn when dead code is identified.
    "-Ywarn-value-discard",    // Warn when non-Unit expression results are unused.
    "-Ywarn-extra-implicit",   // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen",    // Warn when numerical values are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",   // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",    // Warn if a local definition is unused.
    "-Ywarn-unused:params",    // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",   // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates"   // Warn if a private member is unused.
  )

  lazy val scala212               = "2.12.10"
  lazy val scala213               = "2.13.1"
  lazy val supportedScalaVersions = List(scala213, scala212)

  val ScalaVersion = scala213

  val BaseSettings = Seq(
    scalaVersion := ScalaVersion,
    scalacOptions := ScalacOpts,
    organization := "net.scalytica",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    logBuffered in Test := false,
    fork in Test := true,
    javaOptions in Test += "-Dlogger.resource=logback-test.xml",
    turbo := true
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
      .settings(dependencyOverrides := Dependencies.Overrides.overrides)
      .settings(resolvers ++= Dependencies.Resolvers)
      .settings(crossScalaVersions := supportedScalaVersions)
  }

}
// scalastyle:on
