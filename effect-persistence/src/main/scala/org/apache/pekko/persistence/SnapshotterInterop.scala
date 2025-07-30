package org.apache.pekko.persistence

import cats.effect.Sync
import cats.syntax.all.*
import com.evolution.pekko.effect
import com.evolution.pekko.effect.persistence.api.SeqNr
import com.evolutiongaming.catshelper.FromFuture
import org.apache.pekko.persistence.SnapshotProtocol.{DeleteSnapshot, DeleteSnapshots, Request, SaveSnapshot}
import org.apache.pekko.util.Timeout

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object SnapshotterInterop {

  def apply[F[_]: Sync: FromFuture, A](
    snapshotter: Snapshotter,
    timeout: FiniteDuration,
  ): effect.persistence.Snapshotter[F, A] = {

    val timeout1 = Timeout(timeout)

    def snapshotterId = snapshotter.snapshotterId

    def snapshotStore = snapshotter.snapshotStore

    def metadata(seqNr: SeqNr) = SnapshotMetadata(snapshotterId, seqNr)

    def ask[B](a: Request)(pf: PartialFunction[Any, F[B]]): F[F[B]] =
      Sync[F]
        .delay(org.apache.pekko.pattern.ask(snapshotStore, a, snapshotter.self)(timeout1))
        .map { future =>
          FromFuture
            .summon[F]
            .apply(future)
            .flatMap { a =>
              Sync[F].catchNonFatal(pf(a)).flatten
            }
        }

    class Main
    new Main with effect.persistence.Snapshotter[F, A] {

      def save(seqNr: SeqNr, snapshot: A) =
        ask(SaveSnapshot(metadata(seqNr), snapshot)) {
          case a: SaveSnapshotSuccess => Instant.ofEpochMilli(a.metadata.timestamp).pure[F]
          case a: SaveSnapshotFailure => a.cause.raiseError[F, Instant]
        }

      def delete(seqNr: SeqNr) =
        ask(DeleteSnapshot(metadata(seqNr))) {
          case _: DeleteSnapshotSuccess => ().pure[F]
          case a: DeleteSnapshotFailure => a.cause.raiseError[F, Unit]
        }

      def delete(criteria: SnapshotSelectionCriteria) =
        ask(DeleteSnapshots(snapshotterId, criteria)) {
          case _: DeleteSnapshotsSuccess => ().pure[F]
          case a: DeleteSnapshotsFailure => a.cause.raiseError[F, Unit]
        }
    }
  }
}
