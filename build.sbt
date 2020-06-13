name := "tp-arq-concurrentes"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.6"
//lazy val akkaHttp = "10.1.12"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.akka" %% "akka-http"   % "10.1.12",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.12"
)

/*
resolvers += "Typesafe repository" at
  "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core"  % akkaHttp,
  "com.typesafe.akka" %% "akka-http"       % akkaHttp,
  "com.typesafe.play" %% "play-ws-standalone-json"       % "1.1.8",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-slf4j"      % akkaVersion,
  "de.heikoseeberger" %% "akka-http-play-json"   % "1.17.0",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)
*/