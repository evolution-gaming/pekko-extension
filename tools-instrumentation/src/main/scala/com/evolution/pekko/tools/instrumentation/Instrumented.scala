package com.evolution.pekko.tools.instrumentation

import com.evolution.pekko.tools.util.BlockingTracker.Surround
import com.evolution.pekko.tools.util.{BlockingTracker, ExecutionThreadTracker}
import io.prometheus.client.{Collector, CollectorRegistry, Gauge, Summary}
import org.apache.pekko.dispatch.OverridePekkoRunnable
import org.slf4j.MDC

import java.util.ConcurrentModificationException

trait Instrumented {

  def apply(runnable: Runnable, execute: Runnable => Unit): Unit
}

object Instrumented {
  trait Run { def apply[T](f: () => T): T }

  type Instrument = () => BeforeRun
  type BeforeRun = () => AfterRun
  type AfterRun = () => Unit

  def apply(config: InstrumentedConfig, metrics: Metrics.Of): Instrumented = {

    val instruments: List[Instrument] = {
      val name = {
        val name = config.id.replace(".", "-")
        if (name startsWith "pekko") name else s"pekko-$name"
      }
      val mdc = if (config.mdc) Some(Instrument.Mdc) else None
      val metricsOpt = if (config.metrics) Some(Instrument.metrics(metrics(name))) else None
      val executionTracker = config.executionTracker map { Instrument.executionTracker }
      val result = (mdc ++ metricsOpt ++ executionTracker).toList
      result
    }

    val surround = BlockingTracker(config.blockingTracker)

    apply(instruments, surround)
  }

  def apply(instruments: List[Instrument], surround: Surround): Instrumented = {

    object OverrideRunnable {

      def apply(runnable: Runnable, r: Run): Runnable = runnable match {
        case OverridePekkoRunnable(runnable) => runnable(r)
        case OverrideScalaRunnable(runnable) => runnable(r)
        case runnable => new Default(runnable, r)
      }

      class Default(self: Runnable, r: Run) extends Runnable {
        def run(): Unit = r(() => self.run())
      }
    }

    new Instrumented {

      def apply(runnable: Runnable, execute: Runnable => Unit): Unit = {
        val beforeRuns = for { f <- instruments } yield f()
        val run = new Run {
          def apply[T](run: () => T): T = {
            val afterRuns = for { f <- beforeRuns } yield f()
            try surround(run)
            finally for { f <- afterRuns } f()
          }
        }
        val overridden = OverrideRunnable(runnable, run)
        execute(overridden)
      }
    }
  }

  object Instrument {
    val Empty: Instrument = () => () => () => ()

    val Mdc: Instrument = () => {
      val mdc =
        try MDC.getCopyOfContextMap
        catch { case _: ConcurrentModificationException => null }
      () => {
        if (mdc == null) MDC.clear() else MDC.setContextMap(mdc)
        () => {
          MDC.clear()
        }
      }
    }

    def metrics(metrics: Metrics): Instrument = {
      () =>
        {
          val created = System.nanoTime()
          () => {
            val started = System.nanoTime()
            metrics.queue(started - created)
            () => {
              val stopped = System.nanoTime()
              metrics.run(stopped - started)
            }
          }
        }
    }

    def executionTracker(config: InstrumentedConfig.ExecutionTracker): Instrument = {

      val tracker = ExecutionThreadTracker(
        hangingThreshold = config.hangingThreshold,
        checkInterval = config.checkInterval,
      )

      () => {
        () =>
          {
            val stop = tracker.start()
            () => {
              stop()
            }
          }
      }
    }
  }

  trait Metrics {

    def queue(latencyNanos: Long): Unit

    def run(latencyNanos: Long): Unit
  }

  object Metrics {

    type Prefix = String

    object Prefix {
      val Default = "dispatcher"
    }

    trait Of {
      def apply(dispatcher: String): Metrics
    }

    object Of {

      def apply(registry: CollectorRegistry, prefix: Prefix = Prefix.Default): Of = {

        val latencySummary = Summary
          .build()
          .name(s"${ prefix }_latency")
          .help("Latency in seconds")
          .labelNames("dispatcher", "phase")
          .quantile(0.9, 0.05)
          .quantile(0.99, 0.005)
          .register(registry)

        val workersGauge = Gauge
          .build()
          .name(s"${ prefix }_workers")
          .help("Number of workers")
          .labelNames("dispatcher")
          .register(registry)

        def observeLatency(latencyNanos: Long, phase: String, dispatcher: String) = {
          latencySummary
            .labels(dispatcher, phase)
            .observe(latencyNanos.toDouble / Collector.NANOSECONDS_PER_SECOND)
        }

        dispatcher =>
          new Metrics {

            def queue(latencyNanos: Long) = {
              workersGauge.labels(dispatcher).inc()
              observeLatency(latencyNanos, dispatcher = dispatcher, phase = "queue")
            }

            def run(latencyNanos: Long) = {
              workersGauge.labels(dispatcher).dec()
              observeLatency(latencyNanos, dispatcher = dispatcher, phase = "run")
            }
          }
      }
    }
  }
}
