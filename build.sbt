
name := """livesdodia"""
organization := "br.com.livesdodia"
maintainer := "gustavo@livesdodia.com.br"

version := "0.14"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(guice, ws, ehcache)
libraryDependencies += "com.github.maricn" % "logback-slack-appender" % "1.4.0"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "br.com.livesdodia.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "br.com.livesdodia.binders._"

pipelineStages in Assets := Seq(uglify, digest)