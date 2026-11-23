package org.apache.pekko.persistence

import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Sync, Temporal}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.CatsHelper.OpsCatsHelper
import com.evolutiongaming.catshelper.{SerialRef, ToTry}
import org.apache.pekko.actor.{ActorPath, ActorRef, ActorRefProvider, MinimalActorRef}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*

/**
 * Representation of actor capable of constructing result from multiple messages passed into the
 * actor. Inspired by [[PromiseActorRef]], but result [[R]] is an aggregate from incoming messages
 * rather that first message. Can be used only locally, does _not_ tolerate. [[ActorRef.provider]]
 * and [[ActorRef.path]] functions.
 * @tparam F
 *   The effect type.
 * @tparam R
 *   The result type of the aggregate.
 */
private[persistence] trait LocalActorRef[F[_], R] {

  /**
   * Not actual [[ActorRef]]! It is not serializable, thus can not be passed over network. Under the
   * hood it implements [[ActorRef]] trait by providing function `!` that updates internal state
   * using provided function `receive`. Please check [[LocalActorRef.apply]] docs
   */
  def ref: ActorRef

  /**
   * Semantically blocking while aggregating result
   */
  def res: F[R]

  /**
   * Immediately get current state:
   *   - [[None]] if aggregating not finished yet
   *   - [[Some(Left(Throwable))]] if aggregation failed or timeout happened
   *   - [[Some(Right(r))]] if aggregation completed successfully
   */
  def get: F[Option[Either[Throwable, R]]]
}

private[persistence] object LocalActorRef {

  type M = Any

  /**
   * Self reference to send messages to itself.
   */
  sealed trait Self[F[_]] {
    def apply(m: M): F[Unit]
  }

  def of[F[_]: Async: ToTry, S, R](
    initial: S,
    timeout: FiniteDuration,
  )(
    receiveOf: Self[F] => PartialFunction[(S, M), F[Either[S, R]]],
  ): F[LocalActorRef[F, R]] =
    for {
      deferred <- Deferred[F, LocalActorRef[F, R]]
      self = new Self[F] { def apply(m: M) = deferred.get.flatMap(lar => Sync[F].delay(lar.ref ! m)) }
      receive = receiveOf(self)
      instance <- apply(initial, timeout)(receive)
      _ <- deferred.complete(instance)
    } yield instance

  /**
   * Create new [[LocalActorRef]]
   *
   * @param initial
   *   The initial state of type [[S]].
   * @param timeout
   *   Maximum idle interval. Starting a handled reply renews it, the handler must finish within the
   *   renewed interval, even when no client is waiting.
   * @param receive
   *   The aggregate function defining how to apply incoming message on state or produce final
   *   result: [[Left]] for continue aggregating while [[Right]] for the result.
   * @tparam F
   *   The effect type.
   * @tparam S
   *   The aggregating state type.
   * @tparam R
   *   The final result type.
   * @return
   */
  def apply[F[_]: Temporal: ToTry, S, R](
    initial: S,
    timeout: FiniteDuration,
  )(
    receive: PartialFunction[(S, M), F[Either[S, R]]],
  ): F[LocalActorRef[F, R]] = {

    val F = Temporal[F]

    type Result = Either[Throwable, R]

    sealed trait Status
    case class Pending(updated: FiniteDuration) extends Status {
      def isExpired(now: FiniteDuration): Boolean = now - updated >= timeout
    }
    case class Completed(result: Result) extends Status

    def expired = Completed(Left(new TimeoutException(s"no messages received during period of $timeout")))

    for {
      now <- F.monotonic
      state <- SerialRef.of[F, S](initial)
      status <- F.ref[Status](Pending(now))
      result <- F.deferred[Result]
    } yield new LocalActorRef[F, R] {

      // Readers and the watchdog can observe a reply's deadline while receive holds the state permit.
      private def update(now: FiniteDuration)(onTime: Pending => Status): F[Status] =
        status.flatModify {
          case pending: Pending =>
            val next = if (pending.isExpired(now)) expired else onTime(pending)
            val publish = next match {
              case Completed(r) =>
                result.complete(r).as(next)
              case _ =>
                next.pure[F]
            }
            (next, publish)
          case done => (done, done.pure[F])
        }

      private def observe(current: Status, now: FiniteDuration): F[Status] = current match {
        case p: Pending if p.isExpired(now) =>
          update(now)(identity)
        case _ =>
          current.pure[F]
      }

      private def finish(r: Result): F[Unit] =
        F.monotonic.flatMap(now => update(now)(_ => Completed(r))).void

      private def watchdog: F[Unit] =
        F.monotonic.flatMap { now =>
          status.get.flatMap(observe(_, now)).flatMap {
            case Pending(updated) =>
              F.sleep(timeout - (now - updated).max(Duration.Zero)) >> watchdog
            case Completed(_) =>
              F.unit
          }
        }

      override def ref: ActorRef = new MinimalActorRef {

        override def provider: ActorRefProvider = throw new UnsupportedOperationException()

        override def path: ActorPath = throw new UnsupportedOperationException()

        override def !(
          m: M,
        )(implicit
          sender: ActorRef,
        ): Unit =
          state
            .update { s =>
              val p = s -> m
              F.monotonic.flatMap { now =>
                if (receive.isDefinedAt(p)) {
                  update(now)(_ => Pending(now)) >> receive(p).flatMap {
                    // The next reply or reader checks the deadline published at entry.
                    case Left(next) =>
                      next.pure[F]
                    case Right(r) =>
                      finish(r.asRight).as(s)
                  }
                } else s.pure[F]
              }.handleErrorWith { error =>
                finish(error.asLeft).as(s)
              }
            }
            .toTry
            .get

      }

      override def res: F[R] =
        get.flatMap {
          case Some(result) =>
            result.liftTo[F]
          case None =>
            watchdog.background.surround(result.get).flatMap(_.liftTo[F])
        }

      override def get: F[Option[Either[Throwable, R]]] =
        status.get.flatMap {
          case Completed(r) =>
            r.some.pure[F]
          case p: Pending =>
            F.monotonic.flatMap(observe(p, _)).map {
              case Completed(r) =>
                Some(r)
              case Pending(_) =>
                None
            }
        }
    }
  }

}
