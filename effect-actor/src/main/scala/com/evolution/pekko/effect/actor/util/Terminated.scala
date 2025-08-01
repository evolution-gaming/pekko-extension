package com.evolution.pekko.effect.actor.util

import cats.effect.{Concurrent, Deferred}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.{ActorEffect, ActorRefOf}
import com.evolutiongaming.catshelper.CatsHelper.*
import com.evolutiongaming.catshelper.ToFuture
import org.apache.pekko.actor.{Actor, ActorRef, Props}

trait Terminated[F[_]] {

  def apply(actorRef: ActorRef): F[Unit]

  def apply[A, B](actorEffect: ActorEffect[F, A, B]): F[Unit]
}

object Terminated {

  def apply[F[_]: Concurrent: ToFuture](
    actorRefOf: ActorRefOf[F],
  ): Terminated[F] =
    new Terminated[F] {

      def apply(actorRef: ActorRef) =
        Deferred[F, Unit].flatMap { deferred =>
          def actor() =
            new Actor {

              override def preStart() = {
                context.watch(actorRef)
                ()
              }

              def receive = {
                case org.apache.pekko.actor.Terminated(`actorRef`) =>
                  deferred
                    .complete(())
                    .toFuture
                  ()
              }
            }

          actorRefOf(Props(actor()))
            .use(_ => deferred.get)
            .recover {
              case e: IllegalStateException
                  if e.getMessage == "cannot create children while terminating or terminated" =>
            }
        }

      def apply[A, B](actorEffect: ActorEffect[F, A, B]) =
        apply(actorEffect.toUnsafe)
    }
}
