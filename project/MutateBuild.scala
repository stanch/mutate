import sbt._
import sbt.Keys._

object BuildSettings {
    val buildSettings = Defaults.defaultSettings ++ Seq(
        organization := "org.mutate",
        scalaVersion := "2.10.2",
        scalaOrganization := "org.scala-lang",
        resolvers += Resolver.sonatypeRepo("snapshots")
    )
}

object MutateBuild extends Build {
    import BuildSettings._

    lazy val core: Project = Project(
        "core",
        file("core"),
        settings = buildSettings ++ Seq(
            scalaVersion := "2.10.2-SNAPSHOT",
            scalaOrganization := "org.scala-lang.macro-paradise",
            libraryDependencies <+= (scalaVersion)("org.scala-lang.macro-paradise" % "scala-reflect" % _),
			libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.0.0"
        )
    )

    lazy val tests: Project = Project(
        "tests",
        file("tests"),
        settings = buildSettings ++ Seq(
			libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"
		)
    ) dependsOn (core)
}
