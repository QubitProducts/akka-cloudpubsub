/*
 * Copyright 2016 Qubit (http://www.qubit.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qubit.pubsub.akka

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.testkit.scaladsl.TestSource
import akka.stream.{ActorMaterializer, Attributes, Graph, SinkShape}
import com.google.common.base.Charsets
import com.qubit.pubsub.akka.attributes._
import com.qubit.pubsub.client.{PubSubClient, PubSubMessage, PubSubTopic}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class PubSubSinkTest
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with MockFactory {
  implicit val actorSystem = ActorSystem("pubsub-stream-test")
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  test("Flush batches") {
    val mockClient = mock[PubSubClient]
    val testTopic = PubSubTopic("test", "foo")

    (mockClient.publish _)
      .expects(testTopic, *)
      .onCall { (_, m) =>
        require(m.length == 10)
        val msgIds = for (i <- 1 to m.length) yield s"msg$i"
        Future(msgIds)
      }
      .repeat(10)

    val sinkGraph: Graph[SinkShape[PubSubMessage], NotUsed] =
      new PubSubSink(testTopic, 50.millisecond)
    val sinkAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10)))
    val pubsubSink = Sink.fromGraph(sinkGraph).withAttributes(sinkAttributes)

    val (pub, _) = TestSource
      .probe[Array[Byte]]
      .map(PubSubMessage(_))
      .toMat(pubsubSink)(Keep.both)
      .run()

    Range(0, 100)
      .map(i => s"xxx$i".getBytes(Charsets.UTF_8))
      .foreach(pub.sendNext)
    pub.sendComplete()

    // Give enough time for the final flush to finish
    Try(Thread.sleep(10))
  }

  test("Flush on timer") {
    val mockClient = mock[PubSubClient]
    val testTopic = PubSubTopic("test", "foo")

    (mockClient.publish _)
      .expects(testTopic, *)
      .onCall { (_, m) =>
        require(m.length == 5)
        val msgIds = for (i <- 1 to m.length) yield s"msg$i"
        Future(msgIds)
      }
      .once()

    val sinkGraph: Graph[SinkShape[PubSubMessage], NotUsed] =
      new PubSubSink(testTopic, 10.millisecond)
    val sinkAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10)))
    val pubsubSink = Sink.fromGraph(sinkGraph).withAttributes(sinkAttributes)

    val (pub, _) = TestSource
      .probe[Array[Byte]]
      .map(PubSubMessage(_))
      .toMat(pubsubSink)(Keep.both)
      .run()

    Range(0, 5)
      .map(i => s"xxx$i".getBytes(Charsets.UTF_8))
      .foreach(pub.sendNext)

    // Give enough time for the final flush to finish
    Try(Thread.sleep(30))
  }

  test("Flush on upstream failure") {
    val mockClient = mock[PubSubClient]
    val testTopic = PubSubTopic("test", "foo")

    (mockClient.publish _).expects(testTopic, *).onCall { (t, m) =>
      require(m.length == 4)
      val msgIds = for (i <- 1 to m.length) yield s"msg$i"
      Future(msgIds)
    }

    val sinkGraph: Graph[SinkShape[PubSubMessage], NotUsed] =
      new PubSubSink(testTopic, 50.millisecond)
    val sinkAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10)))
    val pubsubSink = Sink.fromGraph(sinkGraph).withAttributes(sinkAttributes)

    val (pub, _) = TestSource
      .probe[Array[Byte]]
      .map(PubSubMessage(_))
      .toMat(pubsubSink)(Keep.both)
      .run()

    Range(0, 5)
      .map(i => s"xxx$i".getBytes(Charsets.UTF_8))
      .foreach(pub.sendNext)
    pub.sendError(new Exception("oops!"))

    // Give enough time for the final flush to finish
    Try(Thread.sleep(10))
  }

  test("Retry on publish failure") {
    val mockClient = mock[PubSubClient]
    val testTopic = PubSubTopic("test", "foo")

    (mockClient.publish _)
      .expects(testTopic, *)
      .returning(Future.failed(new Exception("oops")))
      .repeat(3)

    val sinkGraph: Graph[SinkShape[PubSubMessage], NotUsed] =
      new PubSubSink(testTopic, 50.millisecond)
    val sinkAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10),
           PubSubStageMaxRetriesAttribute(3),
           PubSubPublishTimeoutAttribute(10.milliseconds),
           PubSubStageRetryJitterAttribute(0, 1)))
    val pubsubSink = Sink.fromGraph(sinkGraph).withAttributes(sinkAttributes)

    val (pub, _) = TestSource
      .probe[Array[Byte]]
      .map(PubSubMessage(_))
      .toMat(pubsubSink)(Keep.both)
      .run()

    Range(0, 11)
      .map(i => s"xxx$i".getBytes(Charsets.UTF_8))
      .foreach(pub.sendNext)
    pub.sendComplete()

    // Give enough time for the retries to finish
    Try(Thread.sleep(200))
  }
}
