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
import com.qubit.pubsub.PubSubIntegrationTest
import com.qubit.pubsub.akka.attributes.{
  PubSubClientAttribute,
  PubSubStageBufferSizeAttribute
}
import com.qubit.pubsub.client.PubSubMessage
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class PubSubSinkIT
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with PubSubIntegrationTest {

  implicit val actorSystem = ActorSystem("pubsub-stream-test")
  implicit val materializer = ActorMaterializer()

  override def testName = "pubsubsink"

  override def beforeAll(): Unit = {
    Await.ready(client.createTopic(testTopic), timeout)
    Await
      .ready(client.createSubscription(testSubscription, testTopic), timeout)
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
    Await.ready(client.deleteSubscription(testSubscription), timeout)
    Await.ready(client.deleteTopic(testTopic), timeout)
  }

  test("PubSubSink success") {
    val sinkGraph: Graph[SinkShape[PubSubMessage], NotUsed] =
      new PubSubSink(testTopic, 1.second)
    val sinkAttributes = Attributes(
      List(PubSubClientAttribute(client), PubSubStageBufferSizeAttribute(30)))
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

    // wait for buffers to flush
    Try(Thread.sleep(1000))

    val output = Await.result(client.pull(testSubscription, 100), timeout)
    client.ack(testSubscription, output.map(m => m.ackId))

    output should not be (null)
    output should have size (100)
    output
      .map(m => new String(m.payload.payload, Charsets.UTF_8))
      .forall(_.startsWith("xxx")) should be(true)
  }
}
