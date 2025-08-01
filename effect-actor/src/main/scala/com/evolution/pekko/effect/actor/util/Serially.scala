package com.evolution.pekko.effect.actor.util

import cats.effect.{Async, Concurrent, Sync}
import cats.syntax.all.*

import java.util.concurrent.atomic.AtomicReference

/**
 * Provides serial access to an internal state.
 *
 * The class differs from [[cats.effect.Ref]] by the ability to execute an effect and a guarantee
 * that the operations will be executed in the same order these arrived given these were called from
 * the same thread.
 */
private[effect] trait Serially[F[_], A] {
  def apply(f: A => F[A]): F[Unit]
}

private[effect] object Serially {

  def apply[F[_]: Async, A](value: A): Serially[F, A] = {

    type Task = A => F[A]

    sealed abstract class S

    object S {
      final case class Idle(value: A) extends S // no tasks are running, `value` is result of last task
      final case class Active(task: Task) extends S // task is running and next `task` is waiting its order
      case object Active extends S // task is running, nothing else scheduled
    }

    val ref = new AtomicReference[S](S.Idle(value))

    val unit = ().asRight[(A, Task)]

    def start(a: A, task: Task) =
      (a, task).tailRecM {
        case (a, task) =>
          for {
            a <- task(a)
            s <- Sync[F].delay {
              ref.getAndUpdate {
                case _: S.Active => S.Active
                case S.Active => S.Idle(a)
                case _: S.Idle => S.Idle(a)
              }
            }
          } yield s match {
            case s: S.Active => (a, s.task).asLeft[Unit]
            case S.Active => unit
            case _: S.Idle => unit
          }
      }

    class Main
    new Main with Serially[F, A] {
      def apply(f: A => F[A]) =
        for {
          d <- Concurrent[F].deferred[Either[Throwable, Unit]]
          t = (a: A) =>
            for {
              b <- f(a).attempt
              _ <- d.complete(b.void)
            } yield b.getOrElse(a)
          s <- Sync[F].delay {
            ref.getAndUpdate {
              case _: S.Idle => S.Active
              case s: S.Active =>
                val task = (a: A) =>
                  Async[F].defer {
                    for {
                      a <- s.task(a)
                      a <- t(a)
                    } yield a
                  }
                S.Active(task)
              case S.Active => S.Active(t)
            }
          }
          _ <- s match {
            case s: S.Idle => start(s.value, t)
            case _: S.Active => Concurrent[F].unit
            case S.Active => Concurrent[F].unit
          }
          a <- d.get
          a <- a.liftTo[F]
        } yield a
    }
  }
}
