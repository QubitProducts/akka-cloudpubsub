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

import com.google.api.services.pubsub.PubsubScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.pubsub.v1.{PublisherGrpc, SubscriberGrpc}
import com.qubit.pubsub.client.PubSubApiConfig
import com.typesafe.scalalogging.LazyLogging
import io.grpc._
import io.grpc.auth.MoreCallCredentials
import io.grpc.netty.{NegotiationType, NettyChannelBuilder}

trait PubSubGrpcClientBase extends LazyLogging {
  def apiConfig: PubSubApiConfig

  lazy private val grpcChannel = createGrpcChannel

  protected def createPublisherStub =
    PublisherGrpc
      .newFutureStub(grpcChannel)
      .withCallCredentials(callCredentials)

  protected def createSubscriberStub =
    SubscriberGrpc
      .newFutureStub(grpcChannel)
      .withCallCredentials(callCredentials)

  private def createGrpcChannel: Channel = {
    logger.info("Creating gRPC channel for: [{}]", apiConfig)
    NettyChannelBuilder
      .forAddress(apiConfig.apiHost, apiConfig.apiPort)
      .negotiationType(if (apiConfig.tlsEnabled) NegotiationType.TLS
      else NegotiationType.PLAINTEXT)
      .build()
  }

  private def callCredentials: CallCredentials = {
    val credentials =
      GoogleCredentials.getApplicationDefault.createScoped(PubsubScopes.all())
    MoreCallCredentials.from(credentials)
  }
}

object PubSubGrpcClientBase {

  sealed trait PubSubRequest extends Product with Serializable {
    def project: String
  }

  final case class ListTopics(override val project: String)
      extends PubSubRequest

}
