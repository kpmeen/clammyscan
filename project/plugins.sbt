logLevel := Level.Warn

resolvers ++= Seq(
//  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
//  Resolver.bintrayRepo("zalando", "maven"),
//  Resolver.typesafeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  // Remove below resolver once the following issues has been resolved:
  // https://issues.jboss.org/projects/JBINTER/issues/JBINTER-21
  "JBoss" at "https://repository.jboss.org/"
)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.4")

// Dependency handling
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.0")

// Formatting and style checking
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"           % "2.2.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// Code coverage
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.6.0")

// TODO: Codacy no longer supports the coverage reporter plugin. Alternative is
//       to use the tool provided here:
//          - https://github.com/codacy/codacy-coverage-reporter

// Release management
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")
