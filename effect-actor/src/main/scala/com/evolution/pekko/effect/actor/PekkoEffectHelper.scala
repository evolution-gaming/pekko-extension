package com.evolution.pekko.effect.actor

import cats.effect.Sync
import cats.syntax.all.*
import com.evolutiongaming.catshelper.CatsHelper.*
import com.evolutiongaming.catshelper.{FromFuture, ToFuture}

import scala.concurrent.Future

object PekkoEffectHelper {

  implicit class IdOpsPekkoEffectHelper[A](val self: A) extends AnyVal {

    def asFuture: Future[A] = Future.successful(self)
  }

  implicit class OpsPekkoEffectHelper[F[_], A](val self: F[A]) extends AnyVal {

    /**
     * Unlike `Concurrent.start`, `startNow` tries to evaluate effect on current thread, unless it
     * is asynchronous
     *
     * @return
     *   outer F[_] is about launching effect, inner F[_] is about effect completed
     */
    def startNow(implicit
      F: Sync[F],
      toFuture: ToFuture[F],
      fromFuture: FromFuture[F],
    ): F[F[A]] =
      Sync[F]
        .delay(self.toFuture)
        .map(future => fromFuture(future))
  }
}
