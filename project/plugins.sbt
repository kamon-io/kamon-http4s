lazy val root = project in file(".") dependsOn(RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x")))
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")