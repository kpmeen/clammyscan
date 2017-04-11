logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases")
)

// Formatting and style checking
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"           % "0.6.8")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("com.github.gseitz" % "sbt-release"            % "1.0.4")
addSbtPlugin("me.lessis"         %% "bintray-sbt"           % "0.3.0")
addSbtPlugin("org.scoverage"     %% "sbt-scoverage"         % "1.5.0")
addSbtPlugin("com.codacy"        %% "sbt-codacy-coverage"   % "1.3.5")
