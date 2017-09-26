Akka Streams Source and Sink for Cloud Pub/Sub
===============================================

This repository contains an [Akka Streams](http://doc.akka.io/docs/akka/2.4/scala/stream/index.html) `Source` and `Sink` implementation that can be used with Google's [Cloud Pub/Sub](https://cloud.google.com/pubsub/) messaging middleware. The Cloud Pub/Sub sinks and sources provided in this repository have been in production at [Qubit](http://www.qubit.com/) for several months and is used to process a Cloud Pub/Sub stream that averages over a billion messages a day in real time.


Usage
=====

Currently only Scala 2.11 artifacts are published.

SBT:

```
"com.qubit" % "akka-cloudpubsub_2.11" % "1.0.0"
```

Maven:

```
<dependency>
    <groupId>com.qubit</groupId>
    <artifactId>akka-cloudpubsub_2.11</artifactId>
    <version>1.0.0</version>
</dependency>
```

From source:

```
sbt localInstall
```


Reading from Cloud Pub/Sub (Source)
-----------------------------------

`PubSubSource` is backpressure aware and only reads from Pub/Sub when there's demand downstream. By default, messages are acked every 10 seconds or when the ack buffer is full (default size 100). The ack interval, buffer size and several other properties are configurable via attributes.


Creating a source reading from the `foo-sub` subscription in the `foo-project` with the default settings:

```scala
Source.fromGraph(new PubSubSource(PubSubSubscription("foo-project", "foo-sub")))
```

Overriding the ack interval to 30 seconds:


```scala
Source.fromGraph(new PubSubSource(PubSubSubscription("foo-project", "foo-sub")), 30.seconds)
```

Customise the ack interval, buffer size, pull timeout, max retries and retry jitter:


```scala
val attributes = Attributes(List(
    PubSubPullTimeoutAttribute(60.seconds),
    PubSubStageBufferSizeAttribute(200),
    PubSubStageMaxRetriesAttribute(100),
    PubSubStageRetryJitterAttribute(1, 5)))
Source.fromGraph(new PubSubSource(PubSubSubscription("foo-project", "foo-sub"), 30.seconds)).withAttributes(attributes)
```



Writing to Cloud Pub/Sub (Sink)
-------------------------------

`PubSubSink` flushes messages either when the buffer is full (size is 100 by default) or after the messages have been in the buffer for `maxDelay` (30 seconds by default).


Creating a sink writing to `foo-topic` topic in the `foo-project` with the default settings:

```scala
Sink.fromGraph(new PubSubSink(PubSubTopic("foo-project", "foo-topic")))
```

Overriding the max delay to 10 seconds:


```scala
Sink.fromGraph(new PubSubSink(PubSubTopic("foo-project", "foo-topic")), 10.seconds)
```

Customise the max delay, buffer size, publish timeout, max retries and retry jitter:


```scala
val attributes = Attributes(List(
    PubSubStageBufferSizeAttribute(200),
    PubSubStageMaxRetriesAttribute(100),
    PubSubStageRetryJitterAttribute(1, 5),
    PubSubPublishTimeoutAttribute(60.seconds)))
Sink.fromGraph(new PubSubSink(PubSubTopic("foo-project", "foo-topic"))).withAttributes(attributes)
```


Using the Cloud Pub/Sub Client
------------------------------

The sink and the source both make use of a custom client library implemented using the gRPC API definitions published by Google. This client can be used as a standalone Pub/Sub client even if you are not interested in using Akka streams.


In almost all cases, you should be using the `RetryingPubSubClient` which supports customizable retry policies using the [Atmos library](http://zman.io/atmos/). Custom retry policies and retry execution contexts need to be provided as implicit parameters to the client when it is created. See `com.qubit.pubsub.client.retry.RetryPolicyDefaults` for the default values used for these parameters.


Creating the default client:


```scala
val client = RetryingPubSubClient(PubSubGrpcClient())
```

Overriding the Pub/Sub endpoint and transport security (for tests using the Pub/Sub emulator):

```scala
val config = PubSubApiConfig(apiHost = "localhost", apiPort = "8897", tlsEnabled = false)
val client = RetryingPubSubClient(PubSubGrpcClient(config))
```


Development
===========

Running Tests
--------------

The integration tests require the Pub/Sub emulator to be running. 

On a new terminal window:

```
mkdir -p /tmp/pubsub && gcloud beta emulators pubsub start --data-dir /tmp/pubsub
```

On another terminal window:

Change directory to source root and

```
eval $(gcloud beta emulators pubsub env-init --data-dir /tmp/pubsub)
sbt test it:test
```


Releasing to Sonatype OSSRH
---------------------------

Ensure that credentials and PGP settings are correctly configured as documented at http://www.scala-sbt.org/release/docs/Using-Sonatype.html

```
sbt release
sbt releaseSonatype
```


Contributing
============

- Please make sure that the code is formatted using [scalafmt](https://olafurpg.github.io/scalafmt/) using the provided formatting configuration
- Add tests to exercise the new additions/modifications
- Create an issue on Github before submitting your pull request

