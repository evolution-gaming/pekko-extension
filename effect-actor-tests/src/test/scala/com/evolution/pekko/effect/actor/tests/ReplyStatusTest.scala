package com.evolution.pekko.effect.actor.tests

import cats.arrow.FunctionK
import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolution.pekko.effect.actor.{ActorRefOf, Envelope, ReplyStatus}
import com.evolution.pekko.effect.testkit.Probe
import com.evolutiongaming.catshelper.{FromFuture, ToFuture}
import org.apache.pekko.actor.{ActorSystem, Status}
import org.apache.pekko.testkit.TestActors
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.control.NoStackTrace

class ReplyStatusTest extends AsyncFunSuite with ActorSuite with Matchers {

  test("toString") {
    `toString`[IO](actorSystem).run()
  }

  test("fromActorRef") {
    `fromActorRef`[IO](actorSystem).run()
  }

  private def `toString`[F[_]: Async](actorSystem: ActorSystem) = {

    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)
    val actorRef = actorRefOf(TestActors.blackholeProps)
    (actorRef, actorRef).tupled.use {
      case (to, from) =>
        val reply = ReplyStatus.fromActorRef[F](to, from.some)
        Sync[F].delay {
          reply.toString shouldEqual s"Reply(${ to.path }, ${ from.path })"
        }
    }
  }

  private def `fromActorRef`[F[_]: Async: ToFuture: FromFuture](actorSystem: ActorSystem) = {
    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)
    val resources = for {
      probe <- Probe.of[F](actorRefOf)
      actorRef <- actorRefOf(TestActors.blackholeProps)
    } yield (probe, actorRef)

    resources.use {
      case (probe, from) => {
        val reply = ReplyStatus.fromActorRef[F](probe.actorEffect.toUnsafe, from.some).mapK(FunctionK.id)
        val error: Throwable = new RuntimeException with NoStackTrace
        for {
          a <- probe.expect[Status.Status]
          _ <- reply.success("msg")
          a <- a
          _ = a shouldEqual Envelope(Status.Success("msg"), from)
          a <- probe.expect[Status.Status]
          _ <- reply.fail(error)
          a <- a
          _ = a shouldEqual Envelope(Status.Failure(error), from)
        } yield {}
      }
    }
  }
}
