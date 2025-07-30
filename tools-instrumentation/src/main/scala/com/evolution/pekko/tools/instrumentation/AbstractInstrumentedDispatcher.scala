package com.evolution.pekko.tools.instrumentation

import com.evolutiongaming.config.ConfigHelper.*
import com.typesafe.config.Config
import org.apache.pekko.dispatch.*

/**
 * Instrumented clone of [[org.apache.pekko.dispatch.DispatcherConfigurator]].
 */
abstract class AbstractInstrumentedDispatcher(config: Config, prerequisites: DispatcherPrerequisites)
extends MessageDispatcherConfigurator(config, prerequisites) {

  private val instance = new Dispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    config.get("throughput-deadline-time"),
    configureExecutor(),
    config.get("shutdown-timeout"),
  ) with InstrumentedDispatcherMixin {

    def metrics = AbstractInstrumentedDispatcher.this.metrics
  }

  def dispatcher(): MessageDispatcher = instance

  def metrics: Instrumented.Metrics.Of
}
