package com.evolution.pekko.tools.util

import com.evolution.pekko.tools.test.ActorSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class BackpressureBufferSpec extends AnyWordSpec with ActorSpec with Matchers {
  "BackpressureBuffer" should {
    "buffer not more then duration specified" in new Scope {
      ref ! "1"
      ref ! "2"
      ref ! "3"

      expectMsg("123")
    }

    "buffer until size reached" in new Scope {
      for { x <- 0 to (size * 2) } ref ! x.toString
      expectMsg("01234")
      expectMsg("56789")

      override def duration = 10.seconds
    }

    "ignore unexpected messages" in new Scope {
      ref ! "1"
      ref ! 1
      ref ! "2"
      ref ! "3"

      expectMsg("123")
    }

    "flush buffer" in new Scope {
      ref ! "1"
      ref ! "2"
      ref ! BackpressureBuffer.Flush
      ref ! "3"
      ref ! BackpressureBuffer.Flush

      expectMsg("12")
      expectMsg("3")
    }
  }

  private trait Scope extends ActorScope {
    val size = 5
    lazy val props = BackpressureBuffer.propsByType[String](xs => testActor ! xs.mkString, duration, size)
    lazy val ref = system actorOf props

    def duration = 1.second
  }
}
