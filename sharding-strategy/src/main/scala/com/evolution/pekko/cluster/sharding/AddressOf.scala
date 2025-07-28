package com.evolution.pekko.cluster.sharding

import org.apache.pekko.actor.{ActorSystem, Address}

trait AddressOf {
  def apply(region: Region): Address
}

object AddressOf {

  def apply(actorSystem: ActorSystem): AddressOf = {
    val absoluteAddress = AbsoluteAddress(actorSystem)
    (region: Region) => absoluteAddress(region.path.address)
  }

  def const(address: Address): AddressOf = new AddressOf {
    def apply(region: Region): Address = address
  }
}
