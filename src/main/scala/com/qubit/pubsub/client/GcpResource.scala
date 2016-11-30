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

import com.qubit.pubsub.client.GcpCollection.GcpCollection

import scala.util.{Success, Try}

object GcpCollection extends Enumeration {
  type GcpCollection = Value

  val Topics = Value("topics")
  val Subscriptions = Value("subscriptions")

}

sealed trait GcpResource extends Product with Serializable {
  def project: String

  def collection: Option[GcpCollection] = None

  def resourceName: Option[String] = None

  def toFqn: String = {
    if (collection.isDefined) {
      s"projects/$project/${collection.get}/${resourceName.get}"
    } else {
      s"projects/$project"
    }
  }

  override def toString = toFqn
}

object GcpResource {

  object Implicits {

    implicit class StringToGcpResource(s: String) {
      val resource = Try(GcpResource(s))

      def isValidPubSubTopicFqn: Boolean = resource match {
        case Success(_: PubSubTopic) => true
        case _ => false
      }

      def isValidPubSubSubscriptionFqn: Boolean = resource match {
        case Success(_: PubSubSubscription) => true
        case _ => false
      }

      def toPubSubTopic: PubSubTopic = resource match {
        case Success(topic: PubSubTopic) => topic
        case _ =>
          throw new IllegalArgumentException(
            s"[$s] is not a valid Cloud Pub/Sub topic FQN")
      }

      def toPubSubSubscription: PubSubSubscription = resource match {
        case Success(sub: PubSubSubscription) => sub
        case _ =>
          throw new IllegalArgumentException(
            s"[$s] is not a valid Cloud Pub/Sub subscription FQN")
      }
    }

  }

  def apply[T >: GcpResource](fqn: String): T = {
    require(fqn != null)
    val components = fqn.split('/')
    require(components.length == 2 || components.length == 4)
    val project = components(1)
    if (components.length == 4) {
      val collection = GcpCollection.withName(components(2))
      val resource = components(3)

      collection match {
        case GcpCollection.Topics => PubSubTopic(project, resource)
        case GcpCollection.Subscriptions =>
          PubSubSubscription(project, resource)
      }
    } else {
      GcpProject(project)
    }
  }
}

final case class GcpProject(override val project: String) extends GcpResource

sealed trait PubSubResource extends GcpResource with Product with Serializable

final case class PubSubTopic(override val project: String, topic: String)
    extends PubSubResource {
  override def collection = Some(GcpCollection.Topics)

  override def resourceName = Some(topic)
}

final case class PubSubSubscription(override val project: String,
                                    subscription: String)
    extends PubSubResource {
  override def collection = Some(GcpCollection.Subscriptions)

  override def resourceName = Some(subscription)
}
