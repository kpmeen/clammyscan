resolvers += Resolver.url("bintray-sbt-plugin-releases", url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")
