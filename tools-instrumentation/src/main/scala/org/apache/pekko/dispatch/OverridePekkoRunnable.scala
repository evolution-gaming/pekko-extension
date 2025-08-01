package org.apache.pekko.dispatch

import com.evolution.pekko.tools.instrumentation.Instrumented.Run

import java.util.concurrent.ForkJoinTask
import scala.PartialFunction.condOpt

object OverridePekkoRunnable {
  def unapply(runnable: Runnable): Option[Run => Runnable] = condOpt(runnable) {
    case runnable: Batchable => new OverrideBatchable(runnable, _)
    case runnable: Mailbox => new OverrideMailbox(runnable, _)
  }

  class OverrideBatchable(self: Batchable, r: Run) extends Batchable {
    def run(): Unit = r(() => self.run())
    def isBatchable: Boolean = self.isBatchable
  }

  class OverrideMailbox(self: Mailbox, r: Run) extends ForkJoinTask[Unit] with Runnable {
    def getRawResult: Unit = self.getRawResult()
    def setRawResult(v: Unit): Unit = self.setRawResult(v)
    def exec(): Boolean = r(() => self.exec())
    def run(): Unit = { exec(); () }
  }
}
