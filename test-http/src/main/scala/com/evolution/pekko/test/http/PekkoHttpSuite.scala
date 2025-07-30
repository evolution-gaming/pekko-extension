package com.evolution.pekko.test.http

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.{Assertion, Succeeded}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PekkoHttpSuite extends AsyncFunSuite {
  import PekkoHttpSuite.*

  test("pekko-http modules are of same version") {
    `pekko-http modules are of same version`
  }
}

object PekkoHttpSuite {

  def `pekko-http modules are of same version`(
    implicit
    executor: ExecutionContext,
  ): Future[Assertion] = {

    def future[A](a: => A): Future[A] = Future.fromTry { Try { a } }

    for {
      config <- future { ConfigFactory.load("reference.conf") }
      system <- future { ActorSystem("http-manifest-suite", config) }
      http <- future { Http(system) }
      _ <- http.shutdownAllConnectionPools()
      _ <- system.terminate()
    } yield Succeeded
  }
}
