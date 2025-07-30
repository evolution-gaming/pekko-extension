package com.evolution.pekko.effect.actor.tests

import cats.arrow.FunctionK
import cats.effect.{Async, IO, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolution.pekko.effect.actor.{ActorRefOf, Ask}
import com.evolutiongaming.catshelper.FromFuture
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestActors
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class AskTest extends AsyncFunSuite with ActorSuite with Matchers {

  test("toString") {
    `toString`[IO](actorSystem).run()
  }

  test("apply") {
    `apply`[IO](actorSystem).run()
  }

  private def `toString`[F[_]: Sync: FromFuture](actorSystem: ActorSystem) = {
    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)
    actorRefOf(TestActors.echoActorProps).use { actorRef =>
      val ask = Ask.fromActorRef[F](actorRef)
      Sync[F].delay {
        ask.toString shouldEqual s"Ask(${ actorRef.path })"
      }
    }
  }

  private def `apply`[F[_]: Async: FromFuture](actorSystem: ActorSystem) = {
    val timeout = 1.second
    val actorRefOf = ActorRefOf.fromActorRefFactory[F](actorSystem)
    actorRefOf(TestActors.echoActorProps).use { actorRef =>
      val ask = Ask.fromActorRef[F](actorRef).mapK(FunctionK.id)
      for {
        a0 <- ask("msg0", timeout)
        a1 <- ask("msg1", timeout, actorRef.some)
        a <- a0
        _ = a shouldEqual "msg0"
        a <- a1
        _ = a shouldEqual "msg1"
      } yield {}
    }
  }
}
