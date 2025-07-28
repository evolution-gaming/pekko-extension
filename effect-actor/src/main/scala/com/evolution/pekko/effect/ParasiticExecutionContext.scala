package com.evolution.pekko.effect

import scala.concurrent.ExecutionContext

object ParasiticExecutionContext {
  def apply(): ExecutionContext = ExecutionContext.parasitic
}
