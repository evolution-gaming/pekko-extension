package com.evolution.pekko.test.actor

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.{Assertion, Succeeded}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PekkoActorSuite extends AsyncFunSuite {
  import PekkoActorSuite.*

  test("pekko modules are of same version") {
    `pekko modules are of same version`
  }
}

object PekkoActorSuite {

  def `pekko modules are of same version`(
    implicit
    executor: ExecutionContext,
  ): Future[Assertion] = {
    def future[A](a: => A): Future[A] = Future.fromTry { Try { a } }

    for {
      config <- future { ConfigFactory.load("reference.conf") }
      system <- future { ActorSystem("actor-manifest-suite", config) }
      _ <- system.terminate()
    } yield Succeeded
  }
}
