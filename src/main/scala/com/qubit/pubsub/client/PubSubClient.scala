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

import scala.concurrent.Future

trait PubSubClient {

  def topicExists(pubSubTopic: PubSubTopic): Future[Boolean]

  def createTopic(pubSubTopic: PubSubTopic): Future[Boolean]

  def deleteTopic(pubSubTopic: PubSubTopic): Future[Boolean]

  def listTopics(project: GcpProject): Future[Seq[String]]

  def listTopicSubscriptions(pubSubTopic: PubSubTopic): Future[Seq[String]]

  def publish(pubSubTopic: PubSubTopic,
              payload: Seq[PubSubMessage]): Future[Seq[String]]

  def subscriptionExists(subscription: PubSubSubscription): Future[Boolean]

  def createSubscription(subscription: PubSubSubscription,
                         topic: PubSubTopic,
                         ackDeadlineSeconds: Int = 300): Future[Boolean]

  def listSubscriptions(project: GcpProject): Future[Seq[String]]

  def deleteSubscription(subscription: PubSubSubscription): Future[Boolean]

  def pull(
      subscription: PubSubSubscription,
      maxMessages: Int = 100,
      returnImmediately: Boolean = false): Future[Seq[ReceivedPubSubMessage]]

  def ack(subscription: PubSubSubscription,
          ackIds: Seq[String]): Future[Boolean]

  def modifyAckDeadline(subscription: PubSubSubscription,
                        ackDeadlineSeconds: Int,
                        ackIds: Seq[String]): Future[Boolean]
}
