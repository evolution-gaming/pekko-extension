package com.evolution.pekko.tools.instrumentation

import com.evolution.pekko.tools.instrumentation.Instrumented.Run

import scala.PartialFunction.condOpt
import scala.concurrent.Batchable

object OverrideScalaRunnable {
  def unapply(runnable: Runnable): Option[Run => Runnable] = condOpt(runnable) {
    case runnable: Batchable => new OverrideBatchable(runnable, _)
  }

  class OverrideBatchable(self: Runnable, r: Run) extends Batchable with Runnable {
    def run(): Unit = r(() => self.run())
  }
}
