package com.evolution.pekko.effect.cluster.sharding

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.ActorRefOf
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolution.pekko.effect.actor.tests.ActorSuite
import com.evolution.pekko.effect.persistence.TypeName
import com.evolution.pekko.effect.testkit.Probe
import com.evolutiongaming.catshelper.LogOf
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.{Actor, Props}
import org.apache.pekko.cluster.sharding.ClusterShardingSettings
import org.apache.pekko.cluster.sharding.ShardCoordinator.LeastShardAllocationStrategy
import org.apache.pekko.cluster.sharding.ShardRegion.Msg
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ClusterShardingTest extends AsyncFunSuite with ActorSuite with Matchers {

  override def config =
    IO {
      ConfigFactory
        .load("ClusterShardingTest.conf")
        .some
    }

  test("start") {
    case object HandOffStopMessage

    val result = for {
      logOf <- LogOf.slf4j[IO].toResource
      log <- logOf(classOf[ClusterShardingTest]).toResource
      clusterSharding <- ClusterSharding.of[IO](actorSystem)
      clusterSharding <- clusterSharding.withLogging1(log).pure[Resource[IO, *]]
      clusterShardingSettings <- IO(ClusterShardingSettings(actorSystem)).toResource
      actorRefOf = ActorRefOf.fromActorRefFactory[IO](actorSystem)
      probe <- Probe.of[IO](actorRefOf)
      props = {
        def actor() = new Actor {
          def receive = {
            case () => sender().tell((), self)
            case HandOffStopMessage => context.stop(self)
          }
        }

        Props(actor())
      }
      shardRegion <- clusterSharding.start(
        TypeName("typeName"),
        props,
        clusterShardingSettings,
        { case a => ("entityId", a) },
        (_: Msg) => "shardId",
        new LeastShardAllocationStrategy(1, 1),
        HandOffStopMessage,
      )
    } yield for {
      a <- probe.expect[Unit]
      _ <- IO(shardRegion.tell((), probe.actorEffect.toUnsafe))
      a <- a
    } yield a.msg
    result
      .use(identity)
      .run()
  }
}
