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
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Attributes, Graph, SourceShape}
import com.qubit.pubsub.akka.attributes._
import com.qubit.pubsub.client.{
  PubSubClient,
  PubSubMessage,
  PubSubSubscription,
  ReceivedPubSubMessage
}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class PubSubSourceTest
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with MockFactory {
  implicit val actorSystem = ActorSystem("pubsub-stream-test")
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  test("Batched read and ack") {
    val mockClient = mock[PubSubClient]
    val subscription = PubSubSubscription("test", "foo")
    val messages = for (i <- 1 to 10)
      yield
        ReceivedPubSubMessage(s"ack$i",
                              PubSubMessage(Array[Byte](0, 1, 1, 0, 0)))
    (mockClient.pull _)
      .expects(subscription, 10, false)
      .returning(Future(messages))
      .repeat(10)

    (mockClient.ack _)
      .expects(subscription, *)
      .returning(Future(true))
      .repeat(10)

    val sourceGraph: Graph[SourceShape[PubSubMessage], NotUsed] =
      new PubSubSource(subscription, 1.millisecond)
    val sourceAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10)))
    val pubsubSource =
      Source.fromGraph(sourceGraph).withAttributes(sourceAttributes)

    val msgList = pubsubSource
      .runWith(TestSink.probe[PubSubMessage])
      .request(100)
      .expectNextN(100)

    msgList should not be (null)
    msgList should have size (100)
  }

  test("Ack on timer") {
    val mockClient = mock[PubSubClient]
    val subscription = PubSubSubscription("test", "foo")
    val messages = for (i <- 1 to 5)
      yield
        ReceivedPubSubMessage(s"ack$i",
                              PubSubMessage(Array[Byte](0, 1, 1, 0, 0)))
    (mockClient.pull _)
      .expects(subscription, 10, false)
      .returning(Future(messages))
      .once()

    (mockClient.ack _).expects(subscription, *).returning(Future(true)).once()

    val sourceGraph: Graph[SourceShape[PubSubMessage], NotUsed] =
      new PubSubSource(subscription, 10.millisecond)
    val sourceAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10)))
    val pubsubSource =
      Source.fromGraph(sourceGraph).withAttributes(sourceAttributes)

    val msgList = pubsubSource
      .runWith(TestSink.probe[PubSubMessage])
      .request(5)
      .expectNextN(5)

    // sleep to allow the ack timer to fire
    Try(Thread.sleep(30))

    msgList should not be (null)
    msgList should have size (5)
  }

  test("Retry on exception") {
    val mockClient = mock[PubSubClient]
    val subscription = PubSubSubscription("test", "foo")

    (mockClient.pull _)
      .expects(subscription, 10, false)
      .returning(
        Future.failed[Seq[ReceivedPubSubMessage]](new Exception("oops")))
      .repeat(3)

    val sourceGraph: Graph[SourceShape[PubSubMessage], NotUsed] =
      new PubSubSource(subscription, 1.millisecond)
    val sourceAttributes = Attributes(
      List(PubSubClientAttribute(mockClient),
           PubSubStageBufferSizeAttribute(10),
           PubSubStageMaxRetriesAttribute(3),
           PubSubPullTimeoutAttribute(10.milliseconds),
           PubSubStageRetryJitterAttribute(0, 1)))
    val pubsubSource =
      Source.fromGraph(sourceGraph).withAttributes(sourceAttributes)

    val error = pubsubSource
      .runWith(TestSink.probe[PubSubMessage])
      .request(100)
      .expectError()

    error should not be (null)
  }

}
