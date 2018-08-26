logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
  Resolver.bintrayRepo("kpmeen", "sbt-plugins"),
  Resolver.bintrayRepo("zalando", "maven"),
  Resolver.typesafeRepo("releases"),
  // Remove below resolver once the following issues has been resolved:
  // https://issues.jboss.org/projects/JBINTER/issues/JBINTER-21
  "JBoss" at "https://repository.jboss.org/"
)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.18")

// Dependency resolution
addSbtPlugin("io.get-coursier" %% "sbt-coursier" % "1.0.3")

// Formatting and style checking
addSbtPlugin("com.geirsson"   % "sbt-scalafmt"           % "1.5.1")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// Code coverage
addSbtPlugin("org.scoverage" %% "sbt-scoverage"       % "1.5.1")
addSbtPlugin("com.codacy"    %% "sbt-codacy-coverage" % "1.3.15")

// Release management
addSbtPlugin("com.github.gseitz" % "sbt-release"          % "1.0.9")
addSbtPlugin("org.foundweekends" % "sbt-bintray"          % "0.5.4")
addSbtPlugin("net.scalytica"     % "sbt-gitlab-changelog" % "0.1.2")
