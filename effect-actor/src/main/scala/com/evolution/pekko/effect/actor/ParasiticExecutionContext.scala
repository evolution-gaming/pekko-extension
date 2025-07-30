package com.evolution.pekko.effect.actor

import scala.concurrent.ExecutionContext

object ParasiticExecutionContext {
  def apply(): ExecutionContext = ExecutionContext.parasitic
}
