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

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.qubit.pubsub.Retry
import com.qubit.pubsub.akka.attributes._
import com.qubit.pubsub.client.{PubSubMessage, PubSubSubscription, ReceivedPubSubMessage}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class PubSubSource(subscription: PubSubSubscription,
                   ackInterval: FiniteDuration = 10.seconds)
    extends GraphStage[SourceShape[PubSubMessage]]
    with LazyLogging {
  val RetryJitter = 5
  val out: Outlet[PubSubMessage] = Outlet(
    s"pubsub-source-${subscription.project}-${subscription.subscription}")

  override def shape: SourceShape[PubSubMessage] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new TimerGraphStageLogic(shape) {

      import com.qubit.pubsub.FuturePool._

      private val client = inheritedAttributes
        .getAttribute(classOf[PubSubClientAttribute], PubSubClientAttribute())
        .client
      private val maxBufferSize = inheritedAttributes
        .getAttribute(classOf[PubSubStageBufferSizeAttribute],
                      PubSubStageBufferSizeAttribute())
        .bufferSize
      private val pullTimeout = inheritedAttributes
        .getAttribute(classOf[PubSubPullTimeoutAttribute],
                      PubSubPullTimeoutAttribute())
        .timeout
      private val maxRetries = inheritedAttributes
        .getAttribute(classOf[PubSubStageMaxRetriesAttribute],
                      PubSubStageMaxRetriesAttribute())
        .maxRetries

      private val jitter = inheritedAttributes.getAttribute(
        classOf[PubSubStageRetryJitterAttribute],
        PubSubStageRetryJitterAttribute())

      private val buffer: mutable.Queue[ReceivedPubSubMessage] =
        mutable.Queue.empty
      private var bufferSize: Int = 0

      private val ackBuffer: mutable.Queue[String] = mutable.Queue.empty
      private var ackBufferSize: Int = 0

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if ((bufferSize == 0) && !isClosed(out)) {
            logger.trace("Pulling next batch from PubSub")
            pullNextBatch()
          }

          val msg = buffer.dequeue()
          bufferSize -= 1
          addToAckQueue(msg.ackId)

          logger.trace(
            s"Pushing message downstream. Buffer size = $bufferSize")
          push(out, msg.payload)
        }

        override def onDownstreamFinish(): Unit = {
          logger.info(
            "Downstream finished. Committing consumed messages so far")
          ackConsumedMessages()
          super.onDownstreamFinish()
        }
      })

      override def preStart(): Unit = {
        super.preStart()
        schedulePeriodicallyWithInitialDelay("ack-timer",
                                             ackInterval,
                                             ackInterval)
      }

      override protected def onTimer(timerKey: Any): Unit = {
        logger.trace("Buffer size = {}. Ack buffer size = {}",
                     bufferSize.toString,
                     ackBufferSize.toString)
        ackConsumedMessages()
      }

      private def addToAckQueue(ackId: String): Unit = {
        ackBuffer.enqueue(ackId)
        ackBufferSize += 1
        if (ackBufferSize >= maxBufferSize) {
          ackConsumedMessages()
        }
      }

      private def pullNextBatch(): Unit = {
        val doPullFromSubscription = () => {
          logger.trace("Pulling {} messages from subscription [{}]",
                       maxBufferSize.toString,
                       subscription)
          val msgListFuture = client.pull(subscription, maxBufferSize)
          Try(Await.result(msgListFuture, pullTimeout))
        }
        val msgListTry = Retry(s"pull from $subscription",
                               doPullFromSubscription,
                               maxRetries,
                               pullTimeout,
                               jitter.minSeconds,
                               jitter.maxSeconds)
        if (msgListTry.isSuccess) {
          buffer.enqueue(msgListTry.get: _*)
          bufferSize = buffer.size
        } else {
          logger.error(s"Pull from $subscription failed. Failing stage")
          failStage(msgListTry.failed.get)
        }
      }

      private def ackConsumedMessages(): Unit = {
        if (ackBuffer.isEmpty) {
          return
        }

        val ackBatch = Queue(ackBuffer: _*)
        ackBuffer.clear()
        ackBufferSize = 0

        logger.trace("Acking {} messages", ackBatch.size.toString)
        client.ack(subscription, ackBatch).onFailure {
          case e: Exception => {
            logger.warn("Failed to ack messages", e)
          }
        }
      }
    }
  }
}
