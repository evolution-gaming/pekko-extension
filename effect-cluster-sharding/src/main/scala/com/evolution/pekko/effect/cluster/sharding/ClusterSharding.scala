package com.evolution.pekko.effect.cluster.sharding

import cats.effect.syntax.all.*
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.cluster.{DataCenter, Role}
import com.evolution.pekko.effect.persistence.TypeName
import com.evolution.pekko.effect.util.Terminated
import com.evolution.pekko.effect.{ActorRefOf, Ask}
import com.evolutiongaming.catshelper.*
import com.evolutiongaming.catshelper.CatsHelper.*
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import org.apache.pekko.cluster.sharding.ShardRegion.*
import org.apache.pekko.cluster.sharding.{ClusterShardingSettings, ShardRegion}

import scala.concurrent.duration.*

trait ClusterSharding[F[_]] {

  /**
   * @see
   *   in [[org.apache.pekko.cluster.sharding.ClusterSharding]] corresponding `def start`
   */
  def start[A](
    typeName: TypeName,
    props: Props,
    settings: ClusterShardingSettings,
    extractEntityId: ShardRegion.ExtractEntityId,
    extractShardId: ShardRegion.ExtractShardId,
    allocationStrategy: ShardAllocationStrategy,
    handOffStopMessage: A,
  ): Resource[F, ActorRef]

  /**
   * @see
   *   in [[org.apache.pekko.cluster.sharding.ClusterSharding]] corresponding `def startProxy`
   */
  def startProxy(
    typeName: TypeName,
    role: Option[Role],
    dataCenter: Option[DataCenter],
    extractEntityId: ShardRegion.ExtractEntityId,
    extractShardId: ShardRegion.ExtractShardId,
  ): Resource[F, ActorRef]

  def regions: F[Set[TypeName]]

  def shards(typeName: TypeName): F[Set[ShardState]]

  /**
   * @see
   *   [[org.apache.pekko.cluster.sharding.ClusterSharding.shardRegion]]
   */
  def shardRegion(typeName: TypeName): F[ActorRef]
}

object ClusterSharding {

  final case class Config(terminateTimeout: FiniteDuration, askTimeout: FiniteDuration)
  object Config {
    val default = Config(1.minute, 30.seconds)
  }

  def of[F[_]: Async: ToFuture: FromFuture](
    actorSystem: ActorSystem,
    config: Config = Config.default,
  ): Resource[F, ClusterSharding[F]] = {

    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)
    val terminated = Terminated[F](actorRefOf)

    def createShardRegion(actorRef: => ActorRef) =
      Resource.make {
        Sync[F].blocking(actorRef)
      } { actorRef =>
        for {
          _ <- Sync[F].delay(actorRef.tell(GracefulShutdown, ActorRef.noSender))
          _ <- terminated(actorRef).timeout(config.terminateTimeout)
        } yield {}
      }

    for {
      clusterSharding <- Sync[F].delay(org.apache.pekko.cluster.sharding.ClusterSharding(actorSystem)).toResource
    } yield new ClusterSharding[F] {

      def start[A](
        typeName: TypeName,
        props: Props,
        settings: ClusterShardingSettings,
        extractEntityId: ShardRegion.ExtractEntityId,
        extractShardId: ShardRegion.ExtractShardId,
        allocationStrategy: ShardAllocationStrategy,
        handOffStopMessage: A,
      ) =
        createShardRegion {
          clusterSharding.start(
            typeName = typeName.value,
            entityProps = props,
            settings = settings,
            extractEntityId = extractEntityId,
            extractShardId = extractShardId,
            allocationStrategy = allocationStrategy,
            handOffStopMessage = handOffStopMessage,
          )
        }

      def startProxy(
        typeName: TypeName,
        role: Option[Role],
        dataCenter: Option[DataCenter],
        extractEntityId: ShardRegion.ExtractEntityId,
        extractShardId: ShardRegion.ExtractShardId,
      ) =
        createShardRegion {
          clusterSharding.startProxy(
            typeName = typeName.value,
            role = role.map(_.value),
            dataCenter = dataCenter.map(_.value),
            extractEntityId = extractEntityId,
            extractShardId = extractShardId,
          )
        }

      def regions: F[Set[TypeName]] =
        clusterSharding.shardTypeNames.map(TypeName(_)).pure[F]

      def shards(typeName: TypeName): F[Set[ShardState]] = {
        val ref = clusterSharding.shardRegion(typeName.value)
        val ask = Ask.fromActorRef[F](ref)
        for {
          send <- ask(GetShardRegionState, config.askTimeout)
          resp <- send
          stat <- resp.castM[F, CurrentShardRegionState]
        } yield stat.shards
      }

      def shardRegion(typeName: TypeName): F[ActorRef] =
        Sync[F].delay {
          clusterSharding.shardRegion(typeName.value)
        }
    }
  }

  implicit class ClusterShardingOps[F[_]](val self: ClusterSharding[F]) extends AnyVal {

    def withLogging1(implicit
      F: BracketThrowable[F],
      measureDuration: MeasureDuration[F],
      logOf: LogOf[F],
    ): F[ClusterSharding[F]] =
      logOf(ClusterSharding.getClass).map(log => withLogging1(log))

    def withLogging1(
      log: Log[F],
    )(implicit
      F: BracketThrowable[F],
      measureDuration: MeasureDuration[F],
    ): ClusterSharding[F] = {

      def measure[A](
        allocate: FiniteDuration => String,
        release: FiniteDuration => String,
        resource: Resource[F, A],
      ): Resource[F, A] = {
        val result = for {
          d <- MeasureDuration[F].start
          a <- resource.allocated
          d <- d
          _ <- log.info(allocate(d))
        } yield {
          val (a1, r) = a
          val r1 = for {
            d <- MeasureDuration[F].start
            a <- r
            d <- d
            _ <- log.info(release(d))
          } yield a
          (a1, r1)
        }
        Resource(result)
      }

      new ClusterSharding[F] {

        def start[A](
          typeName: TypeName,
          props: Props,
          settings: ClusterShardingSettings,
          extractEntityId: ShardRegion.ExtractEntityId,
          extractShardId: ShardRegion.ExtractShardId,
          allocationStrategy: ShardAllocationStrategy,
          handOffStopMessage: A,
        ) =
          measure(
            d => s"$typeName in ${ d.toMillis }ms, role: ${ settings.role }",
            d => s"$typeName release in ${ d.toMillis }ms, role: ${ settings.role }",
            self
              .start(typeName, props, settings, extractEntityId, extractShardId, allocationStrategy, handOffStopMessage),
          )

        def startProxy(
          typeName: TypeName,
          role: Option[Role],
          dataCenter: Option[DataCenter],
          extractEntityId: ShardRegion.ExtractEntityId,
          extractShardId: ShardRegion.ExtractShardId,
        ) =
          measure(
            d => s"$typeName proxy in ${ d.toMillis }ms, role: $role, dataCenter: $dataCenter",
            d => s"$typeName proxy release in ${ d.toMillis }ms, role: $role, dataCenter: $dataCenter",
            self.startProxy(typeName, role, dataCenter, extractEntityId, extractShardId),
          )

        def regions: F[Set[TypeName]] = self.regions

        def shards(typeName: TypeName): F[Set[ShardState]] =
          for {
            d <- MeasureDuration[F].start
            r <- self.shards(typeName)
            d <- d
            _ <- log.info(s"get local shards in ${ d.toMillis }ms")
          } yield r

        def shardRegion(typeName: TypeName): F[ActorRef] =
          for {
            d <- MeasureDuration[F].start
            r <- self.shardRegion(typeName)
            d <- d
            _ <- log.info(s"get local shard region in ${ d.toMillis }ms")
          } yield r
      }
    }
  }
}
