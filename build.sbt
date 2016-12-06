lazy val akkaVersion = "2.4.14"

lazy val commonSettings = Seq(
  organization := "com.qubit",
  name := "akka-cloudpubsub",
  scalaVersion := "2.11.8"
)

lazy val publishSettings = Seq(
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <url>https://github.com/QubitProducts/akka-cloudpubsub</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:QubitProducts/akka-cloudpubsub.git</url>
        <connection>scm:git:git@github.com:QubitProducts/akka-cloudpubsub.git</connection>
      </scm>
      <developers>
        <developer>
          <id>charithe</id>
          <name>Charith Ellawala</name>
        </developer>
      </developers>),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
    .settings(publishSettings: _*)
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
