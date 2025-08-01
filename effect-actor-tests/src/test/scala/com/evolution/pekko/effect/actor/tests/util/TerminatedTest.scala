package com.evolution.pekko.effect.actor.tests.util

import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO, Resource}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolution.pekko.effect.actor.tests.ActorSuite
import com.evolution.pekko.effect.actor.util.Terminated
import com.evolution.pekko.effect.actor.{ActorEffect, ActorRefOf, Call, Receive, ReceiveOf}
import com.evolutiongaming.catshelper.{FromFuture, ToFuture}
import org.apache.pekko.actor.ActorSystem
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.TimeoutException
import scala.concurrent.duration.*

class TerminatedTest extends AsyncFunSuite with ActorSuite with Matchers {

  test("wait for termination") {
    `wait for termination`[IO].run()
  }

  test("already dead actors") {
    `already dead actors`[IO].run()
  }

  test("already dead actor system") {
    `already dead actor system`[IO].run()
  }

  def `wait for termination`[F[_]: Async: ToFuture: FromFuture]: F[Unit] = {

    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)

    val terminatedActor = Terminated(actorRefOf)

    ActorEffect
      .of(actorRefOf, ReceiveOf.const(Receive.const[Call[F, Any, Any]](false.pure[F]).pure[Resource[F, *]]))
      .use { actorEffect =>
        for {
          fiber <- terminatedActor(actorEffect).start
          a <- fiber.joinWithNever.timeout(10.millis).attempt
          _ = a should matchPattern { case Left(_: TimeoutException) => }
        } yield fiber.joinWithNever
      }
      .flatten
  }

  def `already dead actors`[F[_]: Async: ToFuture: FromFuture]: F[Unit] = {
    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)

    val terminatedActor = Terminated(actorRefOf)

    ActorEffect
      .of(actorRefOf, ReceiveOf.const(Receive.const[Call[F, Any, Any]](false.pure[F]).pure[Resource[F, *]]))
      .use(actorEffect => terminatedActor(actorEffect).pure[F])
      .flatten
  }

  def `already dead actor system`[F[_]: Async: ToFuture: FromFuture]: F[Unit] = {
    val actorSystem = ActorSystem()
    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)

    val terminatedActor = Terminated(actorRefOf)

    ActorEffect
      .of(actorRefOf, ReceiveOf.const(Receive.const[Call[F, Any, Any]](false.pure[F]).pure[Resource[F, *]]))
      .use(actorEffect => FromFuture[F].apply(actorSystem.terminate()) *> terminatedActor(actorEffect).pure[F])
      .flatten
  }
}
