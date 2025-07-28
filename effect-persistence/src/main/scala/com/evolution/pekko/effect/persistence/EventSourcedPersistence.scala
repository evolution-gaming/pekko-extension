package com.evolution.pekko.effect.persistence

import cats.effect.Async
import com.evolutiongaming.catshelper.{FromFuture, LogOf, ToTry}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.persistence.{EventStoreInterop, SnapshotStoreInterop}

import scala.concurrent.duration.*

trait EventSourcedPersistence[F[_], S, E] {

  def snapshotStore(eventSourced: EventSourced[?]): F[SnapshotStore[F, S]]

  def eventStore(eventSourced: EventSourced[?]): F[EventStore[F, E]]

}

object EventSourcedPersistence {

  def fromPekkoPlugins[F[_]: Async: FromFuture: ToTry: LogOf](
    system: ActorSystem,
    timeout: FiniteDuration,
    capacity: Int,
  ): EventSourcedPersistence[F, Any, Any] = new EventSourcedPersistence[F, Any, Any] {

    val persistence = org.apache.pekko.persistence.Persistence(system)

    override def snapshotStore(eventSourced: EventSourced[?]): F[SnapshotStore[F, Any]] = {
      val pluginId = eventSourced.pluginIds.snapshot.getOrElse("")
      SnapshotStoreInterop[F](persistence, timeout, pluginId, eventSourced.eventSourcedId)
    }

    override def eventStore(eventSourced: EventSourced[?]): F[EventStore[F, Any]] = {
      val pluginId = eventSourced.pluginIds.journal.getOrElse("")
      EventStoreInterop[F](persistence, timeout, capacity, pluginId, eventSourced.eventSourcedId)
    }
  }

}
