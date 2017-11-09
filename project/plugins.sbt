logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases"),
  // Remove below resolver once the following issues has been resolved:
  // https://issues.jboss.org/projects/JBINTER/issues/JBINTER-21
  "JBoss" at "https://repository.jboss.org/"
)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.6")

// Dependency resolution
addSbtPlugin("io.get-coursier"  %% "sbt-coursier" % "1.0.0-RC13")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"   % "0.3.3")

// Formatting and style checking
addSbtPlugin("com.geirsson"   % "sbt-scalafmt"           % "1.3.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// Code coverage
addSbtPlugin("org.scoverage" %% "sbt-scoverage"       % "1.5.1")
addSbtPlugin("com.codacy"    %% "sbt-codacy-coverage" % "1.3.11")

// Release management
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")
