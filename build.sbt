scalaVersion := "2.11.8"
scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

name := "myretail"
organization := "com.joshewer"
version := "1.0"

val akkaVersion       = "2.4.17"
val akkaHttpVersion   = "10.0.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,

  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,

    "com.typesafe.play" %% "play-json" % "2.5.12",
  "org.redis" %% "scala-redis" % "0.0.25",

  "org.scalatest"     %% "scalatest" % "3.0.1" % "test"
)
