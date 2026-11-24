package org.apache.pekko.persistence

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.evolutiongaming.catshelper.ToTry
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.util.Try

class LocalActorRefTest extends AnyFunSuite with Matchers {

  val poison = 100500
  val timeout = 1.second

  def of = LocalActorRef[IO, Int, Int](0, timeout) {
    case (s, `poison`) => IO(s.asRight)
    case (s, m: Int) => IO((s + m).asLeft)
  }

  test("LocalActorRef can handle messages and produce result") {
    val io = for {
      r <- of
      n <- r.get
      _ = n shouldEqual none
      _ <- IO(r.ref ! 3)
      _ <- IO(r.ref ! 4)
      _ <- IO(r.ref ! poison)
      r <- r.res
      _ = r shouldEqual 7
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef.res semantically blocks until result produced") {
    val io = for {
      d <- IO.deferred[Unit]
      r <- of
      f = r.res >> d.complete {}
      f <- f.start
      d0 <- d.tryGet
      _ = d0 shouldEqual none
      _ <- IO(r.ref ! poison)
      _ <- f.join
      d1 <- d.tryGet
      _ = d1 shouldEqual {}.some
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef should handle concurrent ! operations") {
    val io = for {
      r <- of
      l = List.range(0, 100)
      _ <- l.parTraverse(i => IO(r.ref ! i))
      _ <- IO(r.ref ! poison)
      r <- r.res
      _ = r shouldEqual l.sum
    } yield {}

    io.unsafeRunSync()
  }

  test(s"LocalActorRef should timeout aftet $timeout") {
    val io = for {
      r <- of
      _ <- IO.sleep(timeout * 2)
      e <- r.get
      _ = e match {
        case Some(Left(_: TimeoutException)) => succeed
        case other => fail(s"unexpected result $other")
      }
      e <- r.res.attempt
      _ = e match {
        case Left(_: TimeoutException) => succeed
        case other => fail(s"unexpected result $other")
      }
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef should not timeout while receiving messages within timeout span") {
    val io = for {
      r <- of
      _ <- IO.sleep(timeout / 2)
      _ <- IO(r.ref ! 2)
      _ <- IO.sleep(timeout / 2)
      _ <- IO(r.ref ! 3)
      _ <- IO.sleep(timeout / 2)
      _ <- IO(r.ref ! poison)
      r <- r.res
      _ = r shouldEqual 5
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef should keep the timeout result if a message comes after the timeout") {
    val io = for {
      r <- of
      _ <- IO.sleep(timeout * 2)
      _ <- IO(r.ref ! poison)
      e <- r.res.attempt
      _ = e match {
        case Left(_: TimeoutException) => succeed
        case other => fail(s"unexpected result $other")
      }
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef.res should timeout while awaited") {
    val io = for {
      r <- of
      _ <- IO.sleep(timeout / 2)
      _ <- IO(r.ref ! 2)
      e <- r.res.attempt.timed
      (elapsed, result) = e
      _ = result match {
        case Left(_: TimeoutException) => succeed
        case other => fail(s"unexpected result $other")
      }
      // the timeout counts from the last message, not from the creation
      _ = elapsed should be > timeout * 3 / 4
      _ = elapsed should be < timeout * 2
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef should produce result after cancelled res") {
    val io = for {
      r <- of
      f <- r.res.start
      _ <- IO.sleep(timeout / 10)
      _ <- f.cancel
      _ <- IO(r.ref ! 3)
      _ <- IO(r.ref ! poison)
      r <- r.res
      _ = r shouldEqual 3
    } yield {}

    io.unsafeRunSync()
  }

  test("LocalActorRef.! should have no async boundaries if receive has none") {
    val syncOnly = new ToTry[IO] {
      def apply[A](fa: IO[A]): Try[A] =
        fa.syncStep(Int.MaxValue).attempt.unsafeRunSync().flatMap {
          case Right(a) => a.asRight[Throwable]
          case Left(_) => new IllegalStateException("async boundary").asLeft[A]
        }.toTry
    }
    val receive: PartialFunction[(Int, Any), IO[Either[Int, Int]]] = {
      case (s, `poison`) => IO(s.asRight)
      case (s, m: Int) => IO((s + m).asLeft)
    }
    val io = for {
      r <- LocalActorRef[IO, Int, Int](0, timeout)(receive)(IO.asyncForIO, syncOnly)
      f <- r.res.start
      _ <- IO.sleep(timeout / 10)
      _ <- IO(r.ref ! 3)
      _ <- IO(r.ref ! poison)
      r <- f.joinWithNever
      _ = r shouldEqual 3
    } yield {}

    io.unsafeRunSync()
  }

}
