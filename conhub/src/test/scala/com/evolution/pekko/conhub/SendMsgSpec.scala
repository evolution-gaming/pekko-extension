package com.evolution.pekko.conhub

import com.evolution.pekko.conhub.transport.{ReceiveMsg, SendMsg}
import com.evolution.pekko.tools.test.ActorSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SendMsgSpec extends AnyWordSpec with ActorSpec with Matchers {

  override def config: Config = ConfigFactory.load("pekko-cluster-test.conf")

  "do not allow create SendMsg without conhub's role" in {
    val role = "dummy"
    val caught = intercept[RuntimeException] {
      SendMsg[Unit]("", ReceiveMsg.empty, system, role)
    }
    caught.getMessage shouldEqual s"Current node doesn't contain conhub's role ${ role }"
  }
}
