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

package com.qubit.pubsub.client

import org.scalatest.{FunSuite, Matchers}

class GcpResourceTest extends FunSuite with Matchers {

  import GcpResource.Implicits._

  test("Validate correct topic FQN") {
    "projects/test/topics/test-topic".isValidPubSubTopicFqn should be(true)
  }

  test("Validate incorrect topic FQN") {
    "part1/part2/part3/part4".isValidPubSubTopicFqn should be(false)
  }

  test("Validate topic FQN as subscription") {
    "projects/test/topics/test-topic".isValidPubSubSubscriptionFqn should be(
      false)
  }

  test("Validate correct subscription FQN") {
    "projects/test/subscriptions/test-sub".isValidPubSubSubscriptionFqn should be(
      true)
  }

  test("Validate incorrect subscription FQN") {
    "part1/part2/part3/part4".isValidPubSubSubscriptionFqn should be(false)
  }

  test("Validate subscription FQN as topic") {
    "projects/test/subscriptions/test-sub".isValidPubSubTopicFqn should be(
      false)
  }

  test("Convert correct FQN to topic") {
    val topic = "projects/test/topics/test-topic".toPubSubTopic
    topic.project should be("test")
    topic.resourceName should be(Some("test-topic"))
  }

  test("Convert incorrect FQN to topic") {
    intercept[IllegalArgumentException] {
      "projects/test/subscriptions/test-sub".toPubSubTopic
    }
  }

  test("Convert correct FQN to subscription") {
    val sub = "projects/test/subscriptions/test-sub".toPubSubSubscription
    sub.project should be("test")
    sub.resourceName should be(Some("test-sub"))
  }

  test("Convert incorrect FQN to subscription") {
    intercept[IllegalArgumentException] {
      "projects/test/topics/test-topic".toPubSubSubscription
    }
  }

  test("ToFqn") {
    PubSubTopic("test", "test-topic").toFqn should be(
      "projects/test/topics/test-topic")
    PubSubSubscription("test", "test-sub").toFqn should be(
      "projects/test/subscriptions/test-sub")
  }
}
