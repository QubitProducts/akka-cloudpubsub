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

class PubSubSourceIT
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with PubSubIntegrationTest {

  implicit val actorSystem = ActorSystem("pubsub-stream-test")
  implicit val materializer = ActorMaterializer()

  override def testName = "pubsubsource"

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

  test("PubSubSource success") {
    val data = Range(0, 100)
      .map(i => s"msg$i".getBytes(Charsets.UTF_8))
      .map(PubSubMessage(_))
    Await.ready(client.publish(testTopic, data), timeout)

    val sourceGraph: Graph[SourceShape[PubSubMessage], NotUsed] =
      new PubSubSource(testSubscription, 1.millisecond)
    val sourceAttributes = Attributes(
      List(PubSubClientAttribute(client), PubSubStageBufferSizeAttribute(30)))
    val pubsubSource =
      Source.fromGraph(sourceGraph).withAttributes(sourceAttributes)

    val msgList = pubsubSource
      .runWith(TestSink.probe[PubSubMessage])
      .request(100)
      .expectNextN(100)

    msgList should not be (null)
    msgList should have size (100)
    msgList
      .map(m => new String(m.payload, Charsets.UTF_8))
      .forall(_.startsWith("msg")) should be(true)
  }
}
