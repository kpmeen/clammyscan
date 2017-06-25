logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases")
)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.0")

// Dependency resolution
addSbtPlugin("io.get-coursier"  %% "sbt-coursier" % "1.0.0-RC5")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"   % "0.3.0")

// Formatting and style checking
addSbtPlugin("com.geirsson"   % "sbt-scalafmt"           % "0.6.8")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

// Code coverage
addSbtPlugin("org.scoverage" %% "sbt-scoverage"       % "1.5.0")
addSbtPlugin("com.codacy"    %% "sbt-codacy-coverage" % "1.3.8")

// Release management
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.5")
addSbtPlugin("me.lessis"         %% "bintray-sbt" % "0.3.0")
