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

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, Inlet, SinkShape}
import com.qubit.pubsub.Retry
import com.qubit.pubsub.akka.attributes._
import com.qubit.pubsub.client.{PubSubMessage, PubSubTopic}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class PubSubSink(topic: PubSubTopic, maxDelay: FiniteDuration = 30.seconds)
    extends GraphStage[SinkShape[PubSubMessage]]
    with LazyLogging {

  val in: Inlet[PubSubMessage] = Inlet(
    s"pubsub-sink-${topic.project}-${topic.topic}")

  override def shape: SinkShape[PubSubMessage] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new TimerGraphStageLogic(shape) {

      private val publishTimeout = inheritedAttributes
        .getAttribute(classOf[PubSubPublishTimeoutAttribute],
                      PubSubPublishTimeoutAttribute())
        .timeout

      private val client = inheritedAttributes
        .getAttribute(classOf[PubSubClientAttribute], PubSubClientAttribute())
        .client
      private val maxBufferSize = inheritedAttributes
        .getAttribute(classOf[PubSubStageBufferSizeAttribute],
                      PubSubStageBufferSizeAttribute())
        .bufferSize
      private val maxRetries = inheritedAttributes
        .getAttribute(classOf[PubSubStageMaxRetriesAttribute],
                      PubSubStageMaxRetriesAttribute())
        .maxRetries

      private val jitter = inheritedAttributes.getAttribute(
        classOf[PubSubStageRetryJitterAttribute],
        PubSubStageRetryJitterAttribute())

      private val buffer: mutable.Queue[PubSubMessage] = mutable.Queue.empty
      private var bufferSize: Int = 0

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          if (bufferSize >= maxBufferSize) {
            publishBufferedMessages()
          }

          if (isAvailable(in)) {
            buffer.enqueue(grab(in))
            bufferSize += 1
          }

          doPull
        }

        override def onUpstreamFinish(): Unit = {
          logger.debug("Upstream finished. Flushing buffers.")
          publishBufferedMessages()
          super.onUpstreamFinish()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          logger.warn("Upstream failure. Flushing buffers.", ex)
          publishBufferedMessages()
          super.onUpstreamFailure(ex)
        }
      })

      override def preStart(): Unit = {
        super.preStart()
        schedulePeriodicallyWithInitialDelay("publish-timer",
                                             maxDelay,
                                             maxDelay)
        doPull
      }

      override protected def onTimer(timerKey: Any): Unit = {
        publishBufferedMessages()
      }

      private def doPull: Unit = {
        if (!hasBeenPulled(in)) {
          logger.trace("Pulling")
          tryPull(in)
        }
      }

      private def publishBufferedMessages(): Unit = {
        if (buffer.isEmpty) {
          return
        }

        val doPublishToTopic = () => {
          logger.trace("Publishing {} messages to topic [{}]",
                       bufferSize.toString,
                       topic)
          val publishFuture = client.publish(topic, buffer)
          Try(Await.result(publishFuture, publishTimeout))
        }

        val publishTry = Retry(s"publish to $topic",
                               doPublishToTopic,
                               maxRetries,
                               publishTimeout,
                               jitter.minSeconds,
                               jitter.maxSeconds)

        if (publishTry.isSuccess) {
          buffer.clear()
          bufferSize = 0
        } else {
          logger.error(s"Publish to $topic failed. Failing stage")
          failStage(publishTry.failed.get)
        }

      }
    }
  }
}
