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

import java.util.concurrent.Executors

import com.gilt.gfc.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import io.grpc.{Status, StatusRuntimeException}

import scala.concurrent.ExecutionContext
import scala.util.Failure

object RetryPolicyDefaults extends LazyLogging {
  import atmos.dsl._
  import Slf4jSupport._

  import scala.concurrent.duration._

  private val unrecoverableErrorCodes = Set(Status.Code.PERMISSION_DENIED,
                                            Status.Code.UNAUTHENTICATED,
                                            Status.Code.INVALID_ARGUMENT)
  private val rateLimitingErrorCodes =
    Set(Status.Code.RESOURCE_EXHAUSTED, Status.Code.UNAVAILABLE)

  val retryPolicy = retryFor {
    10.attempts
  } using selectedBackoff {
    case Failure(sre: StatusRuntimeException)
        if rateLimitingErrorCodes.contains(sre.getStatus.getCode) =>
      linearBackoff { 50.seconds }
    case _ =>
      exponentialBackoff { 30.seconds } randomized 10.second -> 100.seconds
  } monitorWith {
    logger.underlying
  } onError {
    case sre: StatusRuntimeException
        if unrecoverableErrorCodes.contains(sre.getStatus.getCode) =>
      stopRetrying
  }

  val retryExecCtx = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      10,
      ThreadFactoryBuilder("retry-pool", "retry-worker").build()
    ))
}
