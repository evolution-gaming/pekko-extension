package com.evolution.pekko.cluster.sharding

import org.apache.pekko.actor.{Actor, Address, Props}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AbsoluteAddressSpec extends AnyFunSuite with ActorSpec with Matchers {

  test("AbsoluteAddress") {

    def actor(): Actor = new Actor {
      def receive: Actor.Receive = PartialFunction.empty
    }

    val props = Props(actor())
    val ref = actorSystem.actorOf(props)
    AbsoluteAddress(actorSystem).apply(ref.path.address) shouldEqual Address("pekko", "AbsoluteAddressSpec")
  }
}
