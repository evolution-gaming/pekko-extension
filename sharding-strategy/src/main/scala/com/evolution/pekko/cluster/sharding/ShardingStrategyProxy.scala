package com.evolution.pekko.cluster.sharding

import cats.FlatMap
import cats.syntax.all.*
import com.evolutiongaming.catshelper.FromFuture
import org.apache.pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy

object ShardingStrategyProxy {

  def apply[F[_]: FlatMap: FromFuture](strategy: ShardAllocationStrategy): ShardingStrategy[F] = {

    new ShardingStrategy[F] {

      def allocate(requester: Region, shard: Shard, current: Allocation) = {
        val region = FromFuture[F].apply { strategy.allocateShard(requester, shard, current) }
        for {
          region <- region
        } yield {
          region.some
        }
      }

      def rebalance(current: Allocation, inProgress: Set[Shard]) = {
        val shards = FromFuture[F].apply { strategy.rebalance(current, inProgress) }
        for {
          shards <- shards
        } yield {
          shards.toList
        }
      }
    }
  }
}
