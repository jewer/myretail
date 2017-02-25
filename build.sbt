name := "myretail-microservice"

organization := "com.joshewer"
version := "0.0.1"
scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

val akkaVersion       = "2.4.16"
val akkaHttpVersion   = "10.0.1"

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,

    "com.typesafe.play" %% "play-json" % "2.5.12",
    "org.redis" %% "scala-redis" % "0.0.25",

    "org.scalatest"     %% "scalatest" % "3.0.1" % "test"
  )
}

Revolver.settings
