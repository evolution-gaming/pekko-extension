package org.apache.pekko.persistence

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.{ActorRefOf, AskFrom}
import com.evolution.pekko.effect.persistence.DeleteEventsTo
import com.evolution.pekko.effect.persistence.api.SeqNr
import com.evolutiongaming.catshelper.FromFuture
import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.apache.pekko.persistence.JournalProtocol.DeleteMessagesTo

import scala.concurrent.duration.FiniteDuration

object DeleteEventsToInterop {

  def apply[F[_]: Sync: FromFuture](
    eventsourced: Eventsourced,
    timeout: FiniteDuration,
  ): Resource[F, DeleteEventsTo[F]] =
    apply(Interop(eventsourced), timeout)

  private[persistence] def apply[F[_]: Sync: FromFuture](
    eventsourced: Interop,
    timeout: FiniteDuration,
  ): Resource[F, DeleteEventsTo[F]] = {

    val actorRefOf = ActorRefOf.fromActorRefFactory[F](eventsourced.context.system)

    AskFrom
      .of[F](actorRefOf, eventsourced.self, timeout)
      .map { askFrom =>
        def persistenceId = eventsourced.persistenceId

        def journal = eventsourced.journal

        (seqNr: SeqNr) =>
          askFrom[DeleteMessagesTo, Any](journal)(from => DeleteMessagesTo(persistenceId, seqNr, from))
            .map { result =>
              result.flatMap {
                case _: DeleteMessagesSuccess => ().pure[F]
                case a: DeleteMessagesFailure => a.cause.raiseError[F, Unit]
              }
            }
      }
  }

  trait Interop {

    def persistenceId: String

    def context: ActorContext

    def self: ActorRef

    def journal: ActorRef
  }

  object Interop {

    def apply(eventsourced: Eventsourced): Interop = new Interop {

      def persistenceId = eventsourced.persistenceId

      def context = eventsourced.context

      def self = eventsourced.self

      def journal = eventsourced.journal
    }
  }
}
