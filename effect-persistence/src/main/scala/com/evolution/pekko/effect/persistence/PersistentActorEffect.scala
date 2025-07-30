package com.evolution.pekko.effect.persistence

import cats.effect.{Async, Resource}
import com.evolution.pekko.effect.actor.{ActorEffect, ActorRefOf}
import com.evolutiongaming.catshelper.{FromFuture, ToFuture, ToTry}
import org.apache.pekko.actor.Props

object PersistentActorEffect {

  def of[F[_]: Async: ToFuture: FromFuture: ToTry](
    actorRefOf: ActorRefOf[F],
    eventSourcedOf: PersistentActorOf.Type[F, Any, Any, Any],
    name: Option[String] = None,
  ): Resource[F, ActorEffect[F, Any, Any]] = {

    def actor = PersistentActorOf[F](eventSourcedOf)

    val props = Props(actor)

    actorRefOf(props, name)
      .map(actorRef => ActorEffect.fromActor(actorRef))
  }
}
