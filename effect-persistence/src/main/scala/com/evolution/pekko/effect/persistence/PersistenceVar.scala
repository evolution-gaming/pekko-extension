package com.evolution.pekko.effect.persistence

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.ActorVar.Directive
import com.evolution.pekko.effect.actor.{Act, ActorVar, Envelope, Fail, Receive}
import com.evolution.pekko.effect.persistence.api.SeqNr
import com.evolutiongaming.catshelper.ToFuture
import org.apache.pekko.actor.{ActorContext, ActorRef}

private[effect] trait PersistenceVar[F[_], S, E, C] {

  def preStart(recoveryStarted: Resource[F, RecoveryStarted[F, S, E, Receive[F, Envelope[C], Boolean]]]): Unit

  def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]): Unit

  def event(seqNr: SeqNr, event: E): Unit

  def recoveryCompleted(
    seqNr: SeqNr,
    journaller: Journaller[F, E],
    snapshotter: Snapshotter[F, S],
  ): Unit

  def command(cmd: C, seqNr: SeqNr, sender: ActorRef): Unit

  def timeout(seqNr: SeqNr): Unit

  def postStop(seqNr: SeqNr): F[Unit]
}

private[effect] object PersistenceVar {

  def apply[F[_]: Async: ToFuture: Fail, S, E, C](
    act: Act[F],
    actorContext: ActorContext,
  ): PersistenceVar[F, S, E, C] =
    apply(ActorVar[F, Persistence[F, S, E, C]](act, actorContext), Some(actorContext))

  def apply[F[_]: Sync: Fail, S, E, C](
    actorVar: ActorVar[F, Persistence[F, S, E, C]],
    actorContext: Option[ActorContext] = None,
  ): PersistenceVar[F, S, E, C] =
    new PersistenceVar[F, S, E, C] {

      def preStart(recoveryStarted: Resource[F, RecoveryStarted[F, S, E, Receive[F, Envelope[C], Boolean]]]) =
        actorVar.preStart {
          recoveryStarted.map(recoveryStarted => Persistence.started(recoveryStarted))
        }

      def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]) =
        actorVar.receive { persistence =>
          persistence
            .snapshotOffer(seqNr, snapshotOffer)
            .map(result => Directive.update(result))
            .handleErrorWith(onRecoveryFailed("snapshotOffer"))
        }

      def event(seqNr: SeqNr, event: E) =
        actorVar.receive { persistence =>
          persistence
            .event(seqNr, event)
            .map(result => Directive.update(result))
            .handleErrorWith(onRecoveryFailed("event"))
        }

      def recoveryCompleted(
        seqNr: SeqNr,
        journaller: Journaller[F, E],
        snapshotter: Snapshotter[F, S],
      ) =
        actorVar.receive { persistence =>
          persistence
            .recoveryCompleted(seqNr, journaller, snapshotter)
            .map(result => Directive.update(result))
            .handleErrorWith(onRecoveryFailed("recoveryCompleted"))
        }

      def onRecoveryFailed[A](scope: String)(error: Throwable): F[Directive[A]] =
        Sync[F].delay {
          actorContext.foreach { actorContext =>
            val msg =
              s"$scope failed: ${ actorContext.self.path.toStringWithoutAddress } error recovering actor state: $error"
            actorContext.system.log.error(error, msg)
          }
          Directive.stop
        }

      def command(cmd: C, seqNr: SeqNr, sender: ActorRef) =
        actorVar.receive(_.command(seqNr, cmd, sender))

      def timeout(seqNr: SeqNr) =
        actorVar.receive(_.timeout(seqNr))

      def postStop(seqNr: SeqNr) =
        actorVar.postStop()
    }
}
