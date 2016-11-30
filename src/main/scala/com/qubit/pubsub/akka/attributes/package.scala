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

import akka.stream.Attributes.Attribute
import com.qubit.pubsub.client.PubSubClient
import com.qubit.pubsub.client.grpc.PubSubGrpcClient
import com.qubit.pubsub.client.retry.RetryingPubSubClient

import scala.concurrent.duration._

package object attributes {

  final case class PubSubClientAttribute(
      client: PubSubClient = new RetryingPubSubClient(new PubSubGrpcClient()))
      extends Attribute {
    require(client != null, "Client must not be null")
  }

  final case class PubSubStageBufferSizeAttribute(bufferSize: Int = 100)
      extends Attribute {
    require(bufferSize > 0, "Buffer size must be greater than zero")
  }

  final case class PubSubStageMaxRetriesAttribute(maxRetries: Int = 50)
      extends Attribute {
    require(maxRetries > 0, "Number of retries must not be zero")
  }

  final case class PubSubStageRetryJitterAttribute(minSeconds: Int = 2,
                                                   maxSeconds: Int = 10)
      extends Attribute {
    require(minSeconds >= 0 && maxSeconds > minSeconds,
            "Max seconds must be greater than min seconds")
  }

  final case class PubSubPublishTimeoutAttribute(
      timeout: FiniteDuration = 30.seconds)
      extends Attribute

  final case class PubSubPullTimeoutAttribute(
      timeout: FiniteDuration = 60.seconds)
      extends Attribute

}
