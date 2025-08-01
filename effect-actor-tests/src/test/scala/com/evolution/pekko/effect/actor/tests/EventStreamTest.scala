package com.evolution.pekko.effect.actor.tests

import cats.effect.unsafe.implicits.global
import cats.effect.{Async, Deferred, IO}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.EventStream
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolutiongaming.catshelper.ToFuture
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class EventStreamTest extends AsyncFunSuite with ActorSuite with Matchers {

  test("publish & subscribe") {
    publishAndSubscribe[IO].run()
  }

  private def publishAndSubscribe[F[_]: Async: ToFuture] = {

    case class Event(n: Int)

    val eventStream = EventStream[F](actorSystem)
    for {
      deferred <- Deferred[F, Event]
      onEvent = (event: Event) => deferred.complete(event).void
      actual <- eventStream.subscribe(onEvent).use { _ =>
        eventStream
          .publish(Event(0))
          .productR(deferred.get)
      }
    } yield actual shouldEqual Event(0)
  }
}
