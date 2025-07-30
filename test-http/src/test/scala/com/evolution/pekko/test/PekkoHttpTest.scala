package com.evolution.pekko.test

import com.evolution.pekko.test.http.PekkoHttpSuite.*
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

class PekkoHttpTest extends AsyncFunSuite with Matchers {

  test("pekko-http modules are not of same version") {
    `pekko-http modules are of same version`
      .transform((a: Try[Assertion]) => Success(a))
      .map {
        case Failure(a: IllegalStateException) if a.getMessage contains "pekko-http-testkit" =>
          succeed
        case Failure(unexpectedException) =>
          fail("unexpected exception", unexpectedException)
        case Success(_) =>
          fail("unexpected success")
      }
  }
}
