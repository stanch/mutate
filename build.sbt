name := "mutate"

organization := "me.stanch"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.2"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  compilerPlugin("org.scala-lang.plugins" % "macro-paradise_2.10.2" % "2.0.0-SNAPSHOT"),
  "org.scala-lang" % "scala-reflect" % "2.10.2"
)

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.0",
  "net.virtual-void" %% "json-lenses" % "0.5.3" % "provided",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)