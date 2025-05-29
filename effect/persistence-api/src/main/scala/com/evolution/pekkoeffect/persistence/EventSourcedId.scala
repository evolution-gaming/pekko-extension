package com.evolution.pekkoeffect.persistence

import cats.{Order, Show}

/** @see
  *   [[org.apache.pekko.persistence.PersistentActor.persistenceId]]
  */
final case class EventSourcedId(value: String) {

  override def toString: String = value
}

object EventSourcedId {

  implicit val orderEventSourcedId: Order[EventSourcedId] = Order.by((a: EventSourcedId) => a.value)

  implicit val showEventSourcedId: Show[EventSourcedId] = Show.fromToString
}
