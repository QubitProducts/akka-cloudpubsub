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

import com.gilt.gfc.guava.future.FutureConverters._
import com.google.pubsub.v1._
import com.qubit.pubsub.FuturePool
import com.qubit.pubsub.client._
import io.grpc.{Status, StatusRuntimeException}

import scala.collection.JavaConversions._
import scala.concurrent.Future

class PubSubGrpcClient(
    override val apiConfig: PubSubApiConfig = PubSubApiConfig())
    extends PubSubGrpcClientBase
    with PubSubClient {
  lazy private val publisherStub = createPublisherStub
  lazy private val subscriberStub = createSubscriberStub

  import FuturePool._

  override def topicExists(pubSubTopic: PubSubTopic): Future[Boolean] = {
    logger.debug("Checking for existence of topic [{}]", pubSubTopic)
    val result = publisherStub
      .getTopic(
        GetTopicRequest.newBuilder().setTopic(pubSubTopic.toFqn).build())
      .asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.NOT_FOUND =>
        false
    }
  }

  override def createTopic(pubSubTopic: PubSubTopic): Future[Boolean] = {
    logger.debug("Attempting to create topic [{}]", pubSubTopic)
    val result = publisherStub
      .createTopic(Topic.newBuilder().setName(pubSubTopic.toFqn).build())
      .asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.ALREADY_EXISTS =>
        false
    }
  }

  override def deleteTopic(pubSubTopic: PubSubTopic): Future[Boolean] = {
    logger.debug("Attempting to delete topic [{}]", pubSubTopic)
    val result = publisherStub
      .deleteTopic(
        DeleteTopicRequest.newBuilder().setTopic(pubSubTopic.toFqn).build())
      .asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.NOT_FOUND =>
        false
    }
  }

  override def listTopics(project: GcpProject): Future[Seq[String]] = {
    logger.debug("Requesting topic list from project [{}]", project)
    val result = publisherStub
      .listTopics(
        ListTopicsRequest.newBuilder().setProject(project.toFqn).build())
      .asScala
    result.map(ltr => ltr.getTopicsList.map(_.getName))
  }

  override def listTopicSubscriptions(
      pubSubTopic: PubSubTopic): Future[Seq[String]] = {
    logger.debug("Requesting subscriptions list for topic [{}]", pubSubTopic)
    val result = publisherStub
      .listTopicSubscriptions(
        ListTopicSubscriptionsRequest
          .newBuilder()
          .setTopic(pubSubTopic.toFqn)
          .build())
      .asScala
    result.map(_.getSubscriptionsList)
  }

  override def publish(pubSubTopic: PubSubTopic,
                       payload: Seq[PubSubMessage]): Future[Seq[String]] = {
    logger.trace("Publishing payload to [{}]", pubSubTopic)
    val builder = PublishRequest.newBuilder()
    builder.setTopic(pubSubTopic.toFqn)
    builder.addAllMessages(payload.map(_.toProto))
    val result = publisherStub.publish(builder.build()).asScala
    result.map(_.getMessageIdsList)
  }

  override def subscriptionExists(
      subscription: PubSubSubscription): Future[Boolean] = {
    logger.debug("Checking for existence of subscription [{}]", subscription)
    val result = subscriberStub
      .getSubscription(
        GetSubscriptionRequest
          .newBuilder()
          .setSubscription(subscription.toFqn)
          .build())
      .asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.NOT_FOUND =>
        false
    }
  }

  override def createSubscription(
      subscription: PubSubSubscription,
      topic: PubSubTopic,
      ackDeadlineSeconds: Int = 300): Future[Boolean] = {
    logger.debug(
      "Creating subscription [{}] for topic [{}] with ack deadline [{}s]",
      subscription,
      topic,
      ackDeadlineSeconds.toString)
    val builder = Subscription.newBuilder()
    builder.setAckDeadlineSeconds(ackDeadlineSeconds)
    builder.setName(subscription.toFqn)
    builder.setTopic(topic.toFqn)
    val result = subscriberStub.createSubscription(builder.build()).asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.ALREADY_EXISTS =>
        false
    }
  }

  override def listSubscriptions(project: GcpProject): Future[Seq[String]] = {
    logger.debug("Retrieving list of subscriptions from [{}]", project)
    val result = subscriberStub
      .listSubscriptions(
        ListSubscriptionsRequest
          .newBuilder()
          .setProject(project.toFqn)
          .build())
      .asScala
    result.map(lsr => lsr.getSubscriptionsList.map(_.getName))
  }

  override def deleteSubscription(
      subscription: PubSubSubscription): Future[Boolean] = {
    logger.debug("Deleting subscription [{}]", subscription)
    val result = subscriberStub
      .deleteSubscription(
        DeleteSubscriptionRequest
          .newBuilder()
          .setSubscription(subscription.toFqn)
          .build())
      .asScala
    result.map(_ => true).recover {
      case sre: StatusRuntimeException
          if sre.getStatus.getCode == Status.Code.NOT_FOUND =>
        false
    }
  }

  override def pull(subscription: PubSubSubscription,
                    maxMessages: Int = 100,
                    returnImmediately: Boolean = false)
    : Future[Seq[ReceivedPubSubMessage]] = {
    logger.trace("Pulling from subscription [{}]", subscription)
    val builder = PullRequest.newBuilder()
    builder.setSubscription(subscription.toFqn)
    builder.setMaxMessages(maxMessages)
    builder.setReturnImmediately(returnImmediately)
    val result = subscriberStub.pull(builder.build()).asScala
    result.map(_.getReceivedMessagesList.map(ReceivedPubSubMessage.fromProto))
  }

  override def ack(subscription: PubSubSubscription,
                   ackIds: Seq[String]): Future[Boolean] = {
    logger.trace("Acking messages for subscription [{}]", subscription)
    val builder = AcknowledgeRequest.newBuilder()
    builder.setSubscription(subscription.toFqn)
    builder.addAllAckIds(ackIds)
    val result = subscriberStub.acknowledge(builder.build()).asScala
    result.map(_ => true)
  }

  override def modifyAckDeadline(subscription: PubSubSubscription,
                                 ackDeadlineSeconds: Int,
                                 ackIds: Seq[String]): Future[Boolean] = {
    logger.debug("Modifying ack deadline of [{}] to [{}s]",
                 subscription,
                 ackDeadlineSeconds.toString)
    val builder = ModifyAckDeadlineRequest.newBuilder()
    builder.setSubscription(subscription.toFqn)
    builder.setAckDeadlineSeconds(ackDeadlineSeconds)
    builder.addAllAckIds(ackIds)
    val result = subscriberStub.modifyAckDeadline(builder.build()).asScala
    result.map(_ => true)
  }
}

object PubSubGrpcClient {
  def apply(apiConfig: PubSubApiConfig = PubSubApiConfig()) =
    new PubSubGrpcClient(apiConfig)
}
