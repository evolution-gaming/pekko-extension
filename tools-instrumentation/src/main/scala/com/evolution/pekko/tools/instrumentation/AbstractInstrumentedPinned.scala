package com.evolution.pekko.tools.instrumentation

import com.evolutiongaming.config.ConfigHelper.*
import com.typesafe.config.Config
import org.apache.pekko.dispatch.*
import org.apache.pekko.event.Logging.Warning

/**
 * Instrumented clone of [[org.apache.pekko.dispatch.PinnedDispatcherConfigurator]].
 */
abstract class AbstractInstrumentedPinned(config: Config, prerequisites: DispatcherPrerequisites)
extends MessageDispatcherConfigurator(config, prerequisites) {

  private val threadPoolConfig: ThreadPoolConfig = configureExecutor() match {
    case e: ThreadPoolExecutorConfigurator => e.threadPoolConfig
    case _ =>
      prerequisites.eventStream.publish(
        Warning(
          "PinnedDispatcherConfigurator",
          this.getClass,
          "PinnedDispatcher [%s] not configured to use ThreadPoolExecutor, falling back to default config.".format(
            config.getString("id"),
          ),
        ),
      )
      ThreadPoolConfig()
  }

  override def dispatcher(): MessageDispatcher = {
    new PinnedDispatcher(
      this,
      null,
      config.getString("id"),
      config.get("shutdown-timeout"),
      threadPoolConfig,
    ) with InstrumentedDispatcherMixin {

      def metrics = AbstractInstrumentedPinned.this.metrics
    }
  }

  def metrics: Instrumented.Metrics.Of
}
