package com.evolution.pekko.cluster.sharding

import org.apache.pekko.actor.{Address, ExtendedActorSystem, Extension, ExtensionId}

trait AbsoluteAddress extends Extension {
  def apply(address: Address): Address
}

object AbsoluteAddress extends ExtensionId[AbsoluteAddress] {

  def createExtension(system: ExtendedActorSystem): AbsoluteAddress = new AbsoluteAddress {
    def apply(address: Address): Address = {
      if (address.hasGlobalScope) address
      else system.provider.getDefaultAddress
    }
  }
}
