package com.evolution.pekko.effect.actor

import cats.effect.{MonadCancel, Resource, Sync}
import cats.~>
import org.apache.pekko.actor.{ActorRef, ActorRefFactory, Props}

/**
 * Resource-full api for ActorRefFactory
 *
 * @see
 *   [[org.apache.pekko.actor.ActorRefFactory]]
 */
trait ActorRefOf[F[_]] {

  def apply(
    props: Props,
    name: Option[String] = None,
  ): Resource[F, ActorRef]
}

object ActorRefOf {

  def fromActorRefFactory[F[_]: Sync](
    actorRefFactory: ActorRefFactory,
  ): ActorRefOf[F] = { (props: Props, name: Option[String]) =>
    Resource.make {
      name match {
        case Some(name) => Sync[F].delay(actorRefFactory.actorOf(props, name))
        case None => Sync[F].delay(actorRefFactory.actorOf(props))
      }
    } { actorRef =>
      Sync[F].delay(actorRefFactory.stop(actorRef))
    }
  }

  implicit class ActorRefOfOps[F[_]](val self: ActorRefOf[F]) extends AnyVal {

    def mapK[G[_]](
      f: F ~> G,
    )(implicit
      F: MonadCancel[F, ?],
      G: MonadCancel[G, ?],
    ): ActorRefOf[G] = {
      (props: Props, name: Option[String]) => self(props, name).mapK(f)
    }
  }
}
