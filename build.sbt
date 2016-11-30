lazy val akkaVersion = "2.4.14"

lazy val commonSettings = Seq(
  organization := "com.qubit",
  name := "akka-cloudpubsub",
  scalaVersion := "2.11.8",
  publishMavenStyle := true
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
  .settings(Defaults.itSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "io.grpc" % "grpc-all" % "1.0.1",
      "com.google.auth" % "google-auth-library-oauth2-http" % "0.6.0",
      "com.google.apis" % "google-api-services-pubsub" % "v1-rev14-1.22.0",
      "com.google.api.grpc" % "grpc-google-pubsub-v1" % "0.1.1",
      "com.gilt" %% "gfc-guava" % "0.2.2",
      "io.zman" %% "atmos" % "2.1",
      "io.netty" % "netty-tcnative-boringssl-static" % "1.1.33.Fork22",
      "com.google.guava" % "guava" % "20.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test,it",
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test,it",
      "org.scalatest" %% "scalatest" % "2.2.0" % "test,it",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test,it",
      "ch.qos.logback" % "logback-core" % "1.1.7" % "test,it",
      "ch.qos.logback" % "logback-classic" % "1.1.7" % "test,it"
    )
  )
