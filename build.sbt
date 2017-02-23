resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")

scalaVersion := "2.11.8"

val akkaVersion       = "2.4.17"
val akkaHttpVersion   = "10.0.3"

libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.5.12",

    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,

    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "org.scalatest"     %% "scalatest" % "3.0.1" % "test"
)
