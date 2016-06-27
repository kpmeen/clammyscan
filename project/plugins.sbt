logLevel := Level.Warn

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases")
)

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("me.lessis" %% "bintray-sbt" % "0.3.0")
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.3.5")
addSbtPlugin("com.codacy" %% "sbt-codacy-coverage" % "1.3.0")
addSbtPlugin("org.scalariform" %% "sbt-scalariform" % "1.6.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")
