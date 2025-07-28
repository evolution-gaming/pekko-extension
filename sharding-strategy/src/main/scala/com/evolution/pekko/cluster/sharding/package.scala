package com.evolution.pekko.cluster

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardRegion

package object sharding {

  type Shard = ShardRegion.ShardId
  type Region = ActorRef
  type Allocation = Map[Region, scala.collection.immutable.IndexedSeq[Shard]]

  type Allocate = (Region, Shard, Allocation) => Region

  object Allocate {
    val Default: Allocate = (requester, _, _) => requester
  }
}
