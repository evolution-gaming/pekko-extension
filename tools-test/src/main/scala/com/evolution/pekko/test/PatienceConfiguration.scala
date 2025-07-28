package com.evolution.pekko.test

import org.apache.pekko.testkit.{TestKitExtension, TestKitSettings}
import org.scalatest

import scala.concurrent.duration.*

trait PatienceConfiguration extends scalatest.concurrent.PatienceConfiguration {

  protected lazy val testKitSettings: TestKitSettings = this match {
    case x: ActorSpec => TestKitExtension(x.system)
    case x: ActorSpec#ActorScope => x.testKitSettings
    case _ => new TestKitSettings(TestConfig())
  }

  protected lazy val timeoutDuration: FiniteDuration = {
    val timeout = testKitSettings.DefaultTimeout.duration * testKitSettings.TestTimeFactor
    timeout.asInstanceOf[FiniteDuration]
  }

  protected lazy val defaultPatienceConfig: PatienceConfig = {
    val interval = 200.millis * testKitSettings.TestTimeFactor
    PatienceConfig(timeout = timeoutDuration, interval = interval)
  }

  override implicit def patienceConfig: PatienceConfig = defaultPatienceConfig
}
