package com.evolution.pekkoeffect.persistence

import cats.effect.{Async, Resource}
import com.evolution.pekkoeffect.{ActorEffect, ActorRefOf}
import com.evolutiongaming.catshelper.{FromFuture, LogOf, ToFuture}
import org.apache.pekko.actor.Props

object EventSourcedActorEffect {

  def of[F[_]: Async: ToFuture: FromFuture: LogOf, S, E](
    actorRefOf: ActorRefOf[F],
    eventSourcedOf: EventSourcedOf[F, EventSourcedActorOf.Lifecycle[F, S, E, Any]],
    persistence: EventSourcedPersistence[F, S, E],
    name: Option[String] = None,
  ): Resource[F, ActorEffect[F, Any, Any]] = {

    def actor = EventSourcedActorOf.actor[F, S, E](eventSourcedOf, persistence)

    val props = Props(actor)

    actorRefOf(props, name)
      .map(actorRef => ActorEffect.fromActor(actorRef))
  }

}
