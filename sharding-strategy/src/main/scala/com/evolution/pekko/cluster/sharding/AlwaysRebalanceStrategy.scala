package com.evolution.pekko.cluster.sharding

import cats.effect.Sync
import cats.syntax.all.*
import org.apache.pekko.actor.ActorRef

import scala.util.Random

object AlwaysRebalanceStrategy {

  def apply[F[_]: Sync](random: Random = new Random()): ShardingStrategy[F] = new ShardingStrategy[F] {

    def allocate(requester: ActorRef, shard: Shard, current: Allocation) = {
      Sync[F].delay { random.shuffle(current.keys).headOption }
    }

    def rebalance(current: Allocation, inProgress: Set[Shard]) = {
      val shards = if (current.size == 1) List.empty[Shard] else current.values.flatten.toList
      shards.pure[F]
    }
  }
}
