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

package com.qubit.pubsub.client.retry

import atmos.RetryPolicy
import atmos.dsl._
import com.qubit.pubsub.client.{GcpProject, _}

import scala.concurrent.{ExecutionContext, Future}

class RetryingPubSubClient(underlying: PubSubClient)(
    implicit val policy: RetryPolicy = RetryPolicyDefaults.retryPolicy,
    implicit val ec: ExecutionContext = RetryPolicyDefaults.retryExecCtx)
    extends PubSubClient {
  override def topicExists(pubSubTopic: PubSubTopic): Future[Boolean] =
    retryAsync() {
      underlying.topicExists(pubSubTopic)
    }

  override def createTopic(pubSubTopic: PubSubTopic): Future[Boolean] =
    retryAsync() {
      underlying.createTopic(pubSubTopic)
    }

  override def listTopics(project: GcpProject): Future[Seq[String]] =
    retryAsync() {
      underlying.listTopics(project)
    }

  override def listTopicSubscriptions(
      pubSubTopic: PubSubTopic): Future[Seq[String]] = retryAsync() {
    underlying.listTopicSubscriptions(pubSubTopic)
  }

  override def pull(
      subscription: PubSubSubscription,
      maxMessages: Int,
      returnImmediately: Boolean): Future[Seq[ReceivedPubSubMessage]] =
    retryAsync() {
      underlying.pull(subscription, maxMessages, returnImmediately)
    }

  override def deleteSubscription(
      subscription: PubSubSubscription): Future[Boolean] = retryAsync() {
    underlying.deleteSubscription(subscription)
  }

  override def publish(pubSubTopic: PubSubTopic,
                       payload: Seq[PubSubMessage]): Future[Seq[String]] =
    retryAsync() {
      underlying.publish(pubSubTopic, payload)
    }

  override def subscriptionExists(
      subscription: PubSubSubscription): Future[Boolean] = retryAsync() {
    underlying.subscriptionExists(subscription)
  }

  override def deleteTopic(pubSubTopic: PubSubTopic): Future[Boolean] =
    retryAsync() {
      underlying.deleteTopic(pubSubTopic)
    }

  override def listSubscriptions(project: GcpProject): Future[Seq[String]] =
    retryAsync() {
      underlying.listSubscriptions(project)
    }

  override def createSubscription(subscription: PubSubSubscription,
                                  topic: PubSubTopic,
                                  ackDeadlineSeconds: Int): Future[Boolean] =
    retryAsync() {
      underlying.createSubscription(subscription, topic, ackDeadlineSeconds)
    }

  override def modifyAckDeadline(subscription: PubSubSubscription,
                                 ackDeadlineSeconds: Int,
                                 ackIds: Seq[String]): Future[Boolean] =
    retryAsync() {
      underlying.modifyAckDeadline(subscription, ackDeadlineSeconds, ackIds)
    }

  override def ack(subscription: PubSubSubscription,
                   ackIds: Seq[String]): Future[Boolean] = retryAsync() {
    underlying.ack(subscription, ackIds)
  }
}

object RetryingPubSubClient {
  def apply(underlying: PubSubClient) = new RetryingPubSubClient(underlying)
}
