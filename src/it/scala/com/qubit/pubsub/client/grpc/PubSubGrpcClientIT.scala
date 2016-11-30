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

package com.qubit.pubsub.client.grpc

import com.google.common.base.Charsets
import com.qubit.pubsub.PubSubIntegrationTest
import com.qubit.pubsub.client.{
  GcpProject,
  PubSubMessage,
  PubSubSubscription,
  PubSubTopic
}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await

class PubSubGrpcClientIT
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with PubSubIntegrationTest {
  override def testName = "grpcclient"

  // Operations not supported by the Pub/Sub emulator are ignored

  test("Topic exists") {
    val result1 =
      Await.result(client.topicExists(PubSubTopic(project, "foo")), timeout)
    result1 should be(false)

    Await.ready(client.createTopic(PubSubTopic(project, "foo")), timeout)

    val result2 =
      Await.result(client.topicExists(PubSubTopic(project, "foo")), timeout)
    result2 should be(true)

    Await.ready(client.deleteTopic(PubSubTopic(project, "foo")), timeout)
  }

  test("Create and Delete topic") {
    val result1 =
      Await.result(client.createTopic(PubSubTopic(project, "bar")), timeout)
    result1 should be(true)

    val result2 =
      Await.result(client.createTopic(PubSubTopic(project, "bar")), timeout)
    result2 should be(false)

    val result3 =
      Await.result(client.deleteTopic(PubSubTopic(project, "bar")), timeout)
    result3 should be(true)

    val result4 =
      Await.result(client.deleteTopic(PubSubTopic(project, "bar")), timeout)
    result4 should be(false)
  }

  test("List topics") {
    val result = Await.result(client.listTopics(GcpProject(project)), timeout)
    result should not be (null)
    result.size should be > 0
  }

  test("List topic subscriptions") {
    Await.result(
      client.createSubscription(PubSubSubscription(project, "foo"), testTopic),
      timeout)
    val result =
      Await.result(client.listTopicSubscriptions(testTopic), timeout)
    result should not be (null)
    result.size should be > 0
    result should contain(PubSubSubscription(project, "foo").toFqn)
    Await.result(client.deleteSubscription(PubSubSubscription(project, "foo")),
                 timeout)
  }

  test("Subscription exists") {
    val result1 = Await.result(
      client.subscriptionExists(PubSubSubscription(project, "blah")),
      timeout)
    result1 should be(false)

    Await.result(client.createSubscription(PubSubSubscription(project, "blah"),
                                           testTopic),
                 timeout)
    val result2 = Await.result(
      client.subscriptionExists(PubSubSubscription(project, "blah")),
      timeout)
    result2 should be(true)
    Await.result(
      client.deleteSubscription(PubSubSubscription(project, "blah")),
      timeout)
  }

  test("Create and delete subscription") {
    val result1 = Await.result(
      client.createSubscription(PubSubSubscription(project, "baz"), testTopic),
      timeout)
    result1 should be(true)

    val result2 = Await.result(
      client.createSubscription(PubSubSubscription(project, "baz"), testTopic),
      timeout)
    result2 should be(false)

    val result3 =
      Await.result(
        client.deleteSubscription(PubSubSubscription(project, "baz")),
        timeout)
    result3 should be(true)

    val result4 =
      Await.result(
        client.deleteSubscription(PubSubSubscription(project, "baz")),
        timeout)
    result4 should be(false)
  }

  test("Publish and pull messages") {
    Await.ready(client.createTopic(testTopic), timeout)
    Await.ready(client.createSubscription(testSubscription, testTopic),
                timeout)

    val data = Seq("hello", "world")
      .map(_.getBytes(Charsets.UTF_8))
      .map(PubSubMessage(_))
    val publishResult = Await.result(client.publish(testTopic, data), timeout)
    publishResult should have size 2

    val pullResult = Await.result(client.pull(testSubscription), timeout)
    pullResult should have size 2

    val receivedData = pullResult.map(rm => new String(rm.payload.payload))
    receivedData should contain("hello")
    receivedData should contain("world")

    val ackResult = Await
      .result(client.ack(testSubscription, pullResult.map(_.ackId)), timeout)
    ackResult should be(true)
  }

}
