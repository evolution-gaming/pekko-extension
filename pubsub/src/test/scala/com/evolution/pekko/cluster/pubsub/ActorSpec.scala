package com.evolution.pekko.cluster.pubsub

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Suite}

trait ActorSpec extends BeforeAndAfterAll {
  this: Suite =>
  lazy val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override protected def afterAll(): Unit =
    try
      super.afterAll()
    finally
      TestKit.shutdownActorSystem(actorSystem)

  abstract class ActorScope extends TestKit(actorSystem) with ImplicitSender with DefaultTimeout
}
