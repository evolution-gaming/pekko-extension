package com.evolution.pekko.test

import com.evolution.pekko.test.PekkoActorSuite.*
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

class PekkoActorTest extends AsyncFunSuite with Matchers {

  test("pekko modules are not of same version") {
    `pekko modules are of same version`
      .transform((a: Try[Assertion]) => Success(a))
      .map {
        case Failure(a: IllegalStateException) if a.getMessage contains "pekko-slf4j" =>
          succeed
        case Failure(unexpectedException) =>
          fail("unexpected exception", unexpectedException)
        case Success(_) =>
          fail("unexpected success")
      }
  }
}
