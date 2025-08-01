package com.evolution.pekko.cluster.sharding

import cats.effect.IO
import cats.syntax.all.*
import com.evolution.pekko.cluster.sharding.IOSuite.*
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class AlwaysRebalanceStrategySpec extends AsyncFunSuite with ActorSpec with Matchers {
  private val region = RegionOf(actorSystem)
  private val shard = "shard"

  private val strategy = AlwaysRebalanceStrategy[IO]()

  test("allocate") {
    val allocation = Map((region, IndexedSeq(shard)))
    val result = for {
      region1 <- strategy.allocate(region, shard, allocation)
    } yield {
      region1 shouldEqual region.some
    }
    result.run()
  }

  test("rebalance") {
    val allocation = Map((region, IndexedSeq(shard)))
    val result = for {
      shards <- strategy.rebalance(allocation, Set(shard))
    } yield {
      shards shouldEqual List.empty
    }

    result.run()
  }
}
