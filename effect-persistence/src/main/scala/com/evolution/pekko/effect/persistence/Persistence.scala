package com.evolution.pekko.effect.persistence

import cats.effect.implicits.effectResourceOps
import cats.effect.{Ref, Resource, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.ActorVar.Directive
import com.evolution.pekko.effect.actor.Fail.implicits.*
import com.evolution.pekko.effect.actor.Releasable.implicits.*
import com.evolution.pekko.effect.actor.{Envelope, Fail, Receive, Releasable}
import com.evolution.pekko.effect.persistence.api.SeqNr
import org.apache.pekko.actor.ActorRef

private[effect] trait Persistence[F[_], S, E, C] {

  type Result = Releasable[F, Persistence[F, S, E, C]]

  def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]): F[Result]

  def event(seqNr: SeqNr, event: E): F[Result]

  def recoveryCompleted(
    seqNr: SeqNr,
    journaller: Journaller[F, E],
    snapshotter: Snapshotter[F, S],
  ): F[Result]

  def command(seqNr: SeqNr, cmd: C, sender: ActorRef): F[Directive[Result]]

  def timeout(seqNr: SeqNr): F[Directive[Result]]
}

private[effect] object Persistence {

  private sealed abstract class Started

  def started[F[_]: Sync: Fail, S, E, C](
    recoveryStarted: RecoveryStarted[F, S, E, Receive[F, Envelope[C], Boolean]],
  ): Persistence[F, S, E, C] =
    new Started with Persistence[F, S, E, C] {

      def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]) =
        recoveryStarted(seqNr, snapshotOffer.some).map { recovering =>
          Persistence.recovering(none, recovering)
        }.toReleasable

      def event(seqNr: SeqNr, event: E) = {
        val result = for {
          recovering <- recoveryStarted(seqNr - 1L, none)
          replay <- Allocated.of(recovering.replay)
          _ <- replay.value(event, seqNr).toResource
        } yield Persistence.recovering(replay.some, recovering)
        result.toReleasable
      }

      def recoveryCompleted(
        seqNr: SeqNr,
        journaller: Journaller[F, E],
        snapshotter: Snapshotter[F, S],
      ) = {
        val receive = for {
          recovering <- recoveryStarted(seqNr, none)
          context = Recovering.RecoveryContext(seqNr, journaller, snapshotter)
          receive <- recovering.completed(context)
        } yield Persistence.receive[F, S, E, C](receive)
        receive.toReleasable
      }

      def command(seqNr: SeqNr, cmd: C, sender: ActorRef) =
        unexpected[F, Directive[Result]](name = s"command $cmd", state = "started")

      def timeout(seqNr: SeqNr) =
        unexpected[F, Directive[Result]](name = s"ReceiveTimeout", state = "started")
    }

  private sealed abstract class Recovering1

  def recovering[F[_]: Sync: Fail, S, E, C, R](
    replay: Option[Allocated[F, Replay[F, E]]],
    recovering: Recovering[F, S, E, Receive[F, Envelope[C], Boolean]],
  ): Persistence[F, S, E, C] =
    new Recovering1 with Persistence[F, S, E, C] {

      def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]) =
        unexpected[F, Result](name = s"snapshotOffer $snapshotOffer", state = "receive")

      def event(seqNr: SeqNr, event: E) =
        replay match {
          case Some(replay) =>
            replay
              .value(event, seqNr)
              .as {
                Persistence
                  .recovering(replay.some, recovering)
                  .pure[Releasable[F, *]]
              }

          case None =>
            Allocated
              .of(recovering.replay)
              .flatMap { replay =>
                replay
                  .value(event, seqNr)
                  .as(Persistence.recovering(replay.some, recovering))
                  .toResource
              }
              .toReleasable
        }

      def recoveryCompleted(
        seqNr: SeqNr,
        journaller: Journaller[F, E],
        snapshotter: Snapshotter[F, S],
      ) =
        replay
          .foldMapM(_.release)
          .toResource
          .productR {
            val context = Recovering.RecoveryContext(seqNr, journaller, snapshotter)
            recovering
              .completed(context)
              .map(receive => Persistence.receive[F, S, E, C](receive))
          }
          .toReleasable

      def command(seqNr: SeqNr, cmd: C, sender: ActorRef) =
        unexpected[F, Directive[Result]](name = s"command $cmd", state = "recovering")

      def timeout(seqNr: SeqNr) =
        unexpected[F, Directive[Result]](name = s"ReceiveTimeout", state = "recovering")
    }

  private sealed abstract class Receive1

  def receive[F[_]: Sync: Fail, S, E, C](
    receive: Receive[F, Envelope[C], Boolean],
  ): Persistence[F, S, E, C] =
    new Receive1 with Persistence[F, S, E, C] { self =>
      def snapshotOffer(seqNr: SeqNr, snapshotOffer: SnapshotOffer[S]) =
        unexpected[F, Result](name = s"snapshotOffer $snapshotOffer", state = "receive")

      def event(seqNr: SeqNr, event: E) =
        unexpected[F, Result](name = s"event $event", state = "receive")

      def recoveryCompleted(
        seqNr: SeqNr,
        journaller: Journaller[F, E],
        snapshotter: Snapshotter[F, S],
      ) =
        unexpected[F, Result](name = "recoveryCompleted", state = "receive")

      def command(seqNr: SeqNr, cmd: C, sender: ActorRef) =
        receive(Envelope(cmd, sender)).map {
          case false => Directive.ignore
          case true => Directive.stop
        }

      def timeout(seqNr: SeqNr) =
        receive.timeout.map {
          case false => Directive.ignore
          case true => Directive.stop
        }
    }

  private def unexpected[F[_]: Fail, A](name: String, state: String): F[A] =
    s"$name is not expected in $state".fail[F, A]

  final case class Allocated[F[_], A](value: A, release: F[Unit])

  object Allocated {

    def of[F[_]: Sync, A](a: Resource[F, A]): Resource[F, Allocated[F, A]] =
      Resource.make {
        for {
          ab <- a.allocated
          (a, release) = ab
          ref <- Ref[F].of(release)
        } yield {
          val release = ref.getAndSet(().pure[F]).flatten
          Allocated(a, release)
        }
      }(_.release)
  }
}
