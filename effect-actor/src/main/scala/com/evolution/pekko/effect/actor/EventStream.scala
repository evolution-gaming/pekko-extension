package com.evolution.pekko.effect.actor

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.ToFuture
import org.apache.pekko.actor.{ActorRef, ActorRefFactory, ActorSystem, Props}

import scala.reflect.ClassTag

trait EventStream[F[_]] {

  def publish[A](a: A): F[Unit]

  def subscribe[A](
    onEvent: A => F[Unit],
  )(implicit
    tag: ClassTag[A],
  ): Resource[F, Unit]
}

object EventStream {

  def apply[F[_]: Async: ToFuture](actorSystem: ActorSystem): EventStream[F] =
    apply(actorSystem.eventStream, actorSystem)

  def apply[F[_]: Async: ToFuture](
    eventStream: org.apache.pekko.event.EventStream,
    refFactory: ActorRefFactory,
  ): EventStream[F] = {

    val actorRefOf = ActorRefOf.fromActorRefFactory[F](refFactory)

    new EventStream[F] {

      def publish[A](a: A) =
        Sync[F].delay(eventStream.publish(a))

      def subscribe[A](
        onEvent: A => F[Unit],
      )(implicit
        tag: ClassTag[A],
      ) = {

        val channel = tag.runtimeClass

        def unsubscribe(actorRef: ActorRef): F[Unit] =
          Sync[F].delay(eventStream.unsubscribe(actorRef, channel)).void

        def receiveOf = ReceiveOf[F] { actorCtx =>
          Resource.make {
            Receive[Envelope[Any]] { envelope =>
              tag
                .unapply(envelope.msg)
                .foldMapM { msg =>
                  onEvent(msg).handleError { _ =>
                    ()
                  }
                }
                .as(false)
            } {
              false.pure[F]
            }.pure[F]
          } { _ =>
            unsubscribe(actorCtx.self)
          }
        }

        def subscribe(actorRef: ActorRef) =
          Resource.make {
            Sync[F].delay(eventStream.subscribe(actorRef, channel)).void
          } { _ =>
            unsubscribe(actorRef)
          }

        val props = Props(ActorOf(receiveOf))
        for {
          actorRef <- actorRefOf(props)
          result <- subscribe(actorRef)
        } yield result
      }
    }
  }
}
