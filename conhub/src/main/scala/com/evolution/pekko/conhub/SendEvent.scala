package com.evolution.pekko.conhub

import cats.data.NonEmptyList as Nel
import com.evolution.pekko.conhub.transport.{ReceiveMsg, SendMsg}
import org.apache.pekko.actor.{ActorRefFactory, ActorSystem, Address}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait SendEvent[Id, T] {

  def updated(id: Id, con: T, version: Version): Unit

  def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit

  def removed(id: Id, version: Version): Unit

  def sync(id: Id, con: T, version: Version): Unit
}

object SendEvent {

  def apply[Id, A](
    send: RemoteEvent.Event => Unit,
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[A],
  ): SendEvent[Id, A] =

    new SendEvent[Id, A] {

      def updated(id: Id, con: A, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val conBytes = conSerializer.to(con)
        val value = RemoteEvent.Value(idStr, conBytes, version)
        val updated = RemoteEvent.Event.Updated(value)
        send(updated)
      }

      def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val disconnected = RemoteEvent.Event.Disconnected(idStr, timeout, version)
        send(disconnected)
      }

      def removed(id: Id, version: Version): Unit = {
        val removed = RemoteEvent.Event.Removed(idSerializer.to(id), version)
        send(removed)
      }

      def sync(id: Id, con: A, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val conBytes = conSerializer.to(con)
        val value = RemoteEvent.Value(idStr, conBytes, version)
        val sync = RemoteEvent.Event.Sync(Nel.one(value))
        send(sync)
      }
    }

  def apply[Id, A](
    sendMsg: SendMsg[RemoteEvent],
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[A],
  ): SendEvent[Id, A] = {

    val send = (event: RemoteEvent.Event) => sendMsg(RemoteEvent(event), Nil)
    apply(send, idSerializer, conSerializer)
  }

  def apply[Id, A, M](
    name: String,
    conStates: ConStates[Id, A, M],
    reconnectTimeout: FiniteDuration,
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[A],
    factory: ActorRefFactory,
    conhubRole: String,
  )(implicit
    system: ActorSystem,
    ec: ExecutionContext,
  ): SendEvent[Id, A] = {

    val receive = ReceiveEvent(conStates, reconnectTimeout, idSerializer)
    val send = SendMsg(name, receive, factory, conhubRole)
    apply(send, idSerializer, conSerializer)
  }

  def empty[Id, T]: SendEvent[Id, T] = new SendEvent[Id, T] {
    def updated(id: Id, con: T, version: Version): Unit = {}
    def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit = {}
    def removed(id: Id, version: Version): Unit = {}
    def sync(id: Id, con: T, version: Version): Unit = {}
  }
}

object ReceiveEvent {

  def apply[Id, A, M](
    conStates: ConStates[Id, A, M],
    reconnectTimeout: FiniteDuration,
    idSerializer: Serializer.Str[Id],
  )(implicit
    ec: ExecutionContext,
  ): ReceiveMsg[RemoteEvent] =

    new ReceiveMsg[RemoteEvent] with ConnTypes[A, M] {

      def connected(address: Address) = {
        val ids = for {
          id <- conStates.values.keys
        } yield conStates
          .sync(id)
          .map(_ => id)
          .recover { case _ => id }

        for {
          ids <- Future.sequence(ids)
          id <- conStates.values.keySet.toSet -- ids
        }
          conStates.sync(id)
      }

      def disconnected(address: Address): Unit = {
        val ctx = ConStates.Ctx.Remote(address)
        conStates.values foreach {
          case (id, c: C.Remote) if c.address == address =>
            conStates.disconnect(id, c.version, reconnectTimeout, ctx)

          case _ =>
        }
      }

      def apply(msg: RemoteEvent, address: Address): Unit = {
        val ctx = ConStates.Ctx.Remote(address)

        def onUpdated(value: RemoteEvent.Value): Unit = {
          val id = idSerializer.from(value.id)
          val _ = conStates.update(id, value.version, value.bytes, address)
        }

        def onSync(values: Nel[RemoteEvent.Value]): Unit =
          for {
            value <- values.toList
          } onUpdated(value)

        def onDisconnected(event: RemoteEvent.Event.Disconnected): Unit = {
          val id = idSerializer.from(event.id)
          val _ = conStates.disconnect(id, event.version, event.timeout, ctx)
        }

        def onRemoved(event: RemoteEvent.Event.Removed): Unit = {
          val id = idSerializer.from(event.id)
          val _ = conStates.remove(id, event.version, ctx)
        }

        msg.event match {
          case event: RemoteEvent.Event.Updated => onUpdated(event.value)
          case RemoteEvent.Event.Sync(values) => onSync(values)
          case event: RemoteEvent.Event.Disconnected => onDisconnected(event)
          case event: RemoteEvent.Event.Removed => onRemoved(event)
          case RemoteEvent.Event.ConHubJoined =>
        }
      }
    }
}
