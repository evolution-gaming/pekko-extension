package com.evolution.pekko.cluster.sharding

import cats.Applicative
import cats.syntax.all.*

object RebalanceAllStrategy {

  def apply[F[_]: Applicative](): ShardingStrategy[F] = new ShardingStrategy[F] {

    def allocate(requester: Region, shard: Shard, current: Allocation) = {
      none[Region].pure[F]
    }

    def rebalance(current: Allocation, inProgress: Set[Shard]) = {
      current.values.flatten.toList.pure[F]
    }
  }
}
