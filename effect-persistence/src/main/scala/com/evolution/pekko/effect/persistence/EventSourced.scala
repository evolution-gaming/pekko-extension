package com.evolution.pekko.effect.persistence

import cats.syntax.all.*
import cats.{Functor, Show}
import com.evolution.pekko.effect.persistence.api.EventSourcedId
import org.apache.pekko.persistence.Recovery

/**
 * @param eventSourcedId
 *   \@see [[org.apache.pekko.persistence.PersistentActor.persistenceId]]
 * @param recovery
 *   \@see [[org.apache.pekko.persistence.PersistentActor.recovery]]
 * @param pluginIds
 *   \@see [[org.apache.pekko.persistence.PersistentActor.journalPluginId]]
 * @param value
 *   usually something used to construct instance of an actor
 */
final case class EventSourced[+A](
  eventSourcedId: EventSourcedId,
  recovery: Recovery = Recovery(),
  pluginIds: PluginIds = PluginIds.Empty,
  value: A,
)

object EventSourced {

  implicit val showRecovery: Show[Recovery] = Show.fromToString

  implicit val functorEventSourced: Functor[EventSourced] = new Functor[EventSourced] {
    def map[A, B](fa: EventSourced[A])(f: A => B) = fa.map(f)
  }

  implicit def showEventSourced[A: Show]: Show[EventSourced[A]] = { a =>
    show"${ a.productPrefix }(${ a.eventSourcedId },${ a.pluginIds },${ a.recovery },${ a.value })"
  }

  implicit class EventSourcedOps[A](val self: EventSourced[A]) extends AnyVal {
    def map[B](f: A => B): EventSourced[B] = self.copy(value = f(self.value))
  }
}
