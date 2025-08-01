package com.evolution.pekko.conhub

import cats.data.NonEmptyList as Nel
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

final case class RemoteEvent(event: RemoteEvent.Event)

object RemoteEvent {

  final case class Value(id: String, bytes: ByteVector, version: Version)

  sealed trait Event

  object Event {

    final case class Updated(value: Value) extends Event

    final case class Removed(id: String, version: Version) extends Event

    final case class Disconnected(id: String, timeout: FiniteDuration, version: Version) extends Event

    final case class Sync(values: Nel[Value]) extends Event

    case object ConHubJoined extends Event
  }
}

final case class RemoteMsgs(values: Nel[ByteVector])
