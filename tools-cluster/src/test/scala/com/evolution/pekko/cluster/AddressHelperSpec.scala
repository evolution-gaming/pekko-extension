package com.evolution.pekko.cluster

import org.apache.pekko.actor.Address
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AddressHelperSpec extends AnyFunSpec with Matchers {
  val global = Address("pekko.tcp", "coreservices", "127.0.0.1", 9196)
  val local = Address("pekko", "coreservices")
  val addressHelper = new AddressHelper(global)

  it("toLocal") {
    addressHelper.toLocal(global) shouldEqual local
    addressHelper.toLocal(local) shouldEqual local
  }

  it("toGlobal") {
    addressHelper.toGlobal(global) shouldEqual global
    addressHelper.toGlobal(local) shouldEqual global
  }
}
