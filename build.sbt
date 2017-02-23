import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "FFR Test",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "joda-time" % "joda-time" % "2.9.7",
    libraryDependencies += "org.joda" % "joda-convert" % "1.8",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
  )