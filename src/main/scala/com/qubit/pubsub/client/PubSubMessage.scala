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

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import com.google.protobuf.{ByteString, Timestamp}
import com.google.pubsub.v1.{
  PubsubMessage => PubSubMessageProto,
  ReceivedMessage => ReceivedPubSubMessageProto
}

import scala.collection.JavaConversions._

final case class PubSubMessage(
    payload: Array[Byte],
    msgId: Option[String] = None,
    publishTs: Option[ZonedDateTime] = None,
    attributes: Option[Map[String, String]] = None) {
  def toProto: PubSubMessageProto = {
    val builder = PubSubMessageProto.newBuilder()
    builder.setData(ByteString.copyFrom(payload))
    publishTs.foreach(
      ts =>
        builder.setPublishTime(
          Timestamp.newBuilder().setSeconds(ts.toEpochSecond).build()))
    msgId.foreach(id => builder.setMessageId(id))
    attributes.foreach(attr => builder.putAllAttributes(attr))
    builder.build()
  }
}

object PubSubMessage {
  def fromProto(proto: PubSubMessageProto): PubSubMessage = {
    val payload = proto.getData.toByteArray
    val msgId = Some(proto.getMessageId)
    val attributes = if (proto.getAttributesMap.isEmpty) { None } else {
      Some(proto.getAttributesMap.toMap)
    }
    val publishTs = if (proto.hasPublishTime) {
      Some(
        ZonedDateTime.ofInstant(
          Instant.ofEpochSecond(proto.getPublishTime.getSeconds),
          ZoneOffset.UTC))
    } else {
      None
    }

    PubSubMessage(payload,
                  msgId = msgId,
                  publishTs = publishTs,
                  attributes = attributes)
  }
}

final case class ReceivedPubSubMessage(ackId: String, payload: PubSubMessage)

object ReceivedPubSubMessage {
  def fromProto(proto: ReceivedPubSubMessageProto): ReceivedPubSubMessage = {
    val ackId = proto.getAckId
    val payload = PubSubMessage.fromProto(proto.getMessage)
    ReceivedPubSubMessage(ackId, payload)
  }
}
