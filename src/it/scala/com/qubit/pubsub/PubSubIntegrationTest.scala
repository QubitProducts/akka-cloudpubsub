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

package com.qubit.pubsub

import com.qubit.pubsub.client.grpc.PubSubGrpcClient
import com.qubit.pubsub.client.retry.RetryingPubSubClient
import com.qubit.pubsub.client.{
  PubSubApiConfig,
  PubSubClient,
  PubSubSubscription,
  PubSubTopic
}

import scala.concurrent.duration._

trait PubSubIntegrationTest {
  def testName: String

  lazy val client = createClient
  val timeout = 10.seconds
  val project = "akka-cloudpubsub"
  val testTopic = PubSubTopic(project, s"$testName-topic")
  val testSubscription = PubSubSubscription(project, s"$testName-subscription")

  private def createClient: PubSubClient = {
    // See README for instructions
    val emulator = System.getenv("PUBSUB_EMULATOR_HOST")
    if (emulator == null) {
      println(
        "********** Skipping integration tests because PubSub Emulator is not running **********")
      System.exit(1)
    }

    val hostAndPort = emulator.split(':')
    new RetryingPubSubClient(
      new PubSubGrpcClient(
        PubSubApiConfig(apiHost = hostAndPort(0),
                        apiPort = hostAndPort(1).toInt,
                        tlsEnabled = false)))
  }
}
