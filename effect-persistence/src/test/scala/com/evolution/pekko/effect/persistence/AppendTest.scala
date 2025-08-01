package com.evolution.pekko.effect.persistence

import cats.data.NonEmptyList as Nel
import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO, Ref, Sync}
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.*
import com.evolution.pekko.effect.actor.IOSuite.*
import com.evolution.pekko.effect.persistence.api.{Events, SeqNr}
import com.evolutiongaming.catshelper.CatsHelper.*
import com.evolutiongaming.catshelper.{ToFuture, ToTry}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.Queue
import scala.util.control.NoStackTrace

class AppendTest extends AsyncFunSuite with Matchers {

  test("adapter") {
    val result = for {
      act <- Act.serial[IO]
      _ <- adapter(act)
    } yield {}
    result.run()
  }

  private def adapter[F[_]: Async: ToFuture: ToTry](act: Act[F]): F[Unit] = {

    def eventsourced(act: Act[F], ref: Ref[F, Queue[F[Unit]]]): F[Append.Eventsourced] =
      Ref[F]
        .of(SeqNr.Min)
        .map { seqNr =>
          new Append.Eventsourced {

            def lastSequenceNr = seqNr.get.toTry.get

            def persistAllAsync[A](events: List[A])(handler: A => Unit) = {
              val handlers = for {
                _ <- seqNr.update(_ + events.size)
                _ <- events.foldMapM(event => act(handler(event)))
              } yield {}
              ref
                .update(_.enqueue(handlers))
                .toTry
                .get
            }
          }
        }

    val error: Throwable = new RuntimeException with NoStackTrace
    val stopped: Throwable = new RuntimeException with NoStackTrace

    for {
      ref <- Ref[F].of(Queue.empty[F[Unit]])
      eventsourced <- eventsourced(act, ref)
      result <- Append
        .adapter[F, Int](act, eventsourced, stopped.pure[F])
        .use { append =>
          def dequeue =
            ref.modify { queue =>
              queue.dequeueOption match {
                case Some((a, queue)) => (queue, a)
                case None => (Queue.empty, ().pure[F])
              }
            }.flatten

          for {
            seqNr0 <- append.value(Events.batched(Nel.of(0, 1), Nel.of(2)))
            seqNr1 <- append.value(Events.of(3))
            queue <- ref.get
            _ = queue.size shouldEqual 3
            _ <- dequeue
            _ <- dequeue
            seqNr <- seqNr0
            _ = seqNr shouldEqual 3L
            queue <- ref.get
            _ = queue.size shouldEqual 1
            _ <- dequeue
            seqNr <- seqNr1
            _ = seqNr shouldEqual 4L
            _ <- dequeue
            seqNr0 <- append.value(Events.of(4))
            seqNr1 <- append.value(Events.of(5))
            queue <- ref.get
            _ = queue.size shouldEqual 2
            _ <- Sync[F].delay(append.onError(error, 2, 2L))
            seqNr <- seqNr0.attempt
            _ = seqNr shouldEqual error.asLeft
            seqNr <- seqNr1.attempt
            _ = seqNr shouldEqual error.asLeft
            result <- append.value(Events.of(6))
          } yield result
        }
      result <- result.attempt
      _ = result shouldEqual stopped.asLeft
    } yield {}
  }
}
