package com.evolution.pekko.effect.eventsourcing.util

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.FromFuture
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete

object ResourceFromQueue {

  def apply[F[_]: Sync: FromFuture, A](
    queue: => SourceQueueWithComplete[A],
  ): Resource[F, SourceQueueWithComplete[A]] =
    Resource.make {
      Sync[F].delay(queue)
    } { queue =>
      for {
        _ <- Sync[F].delay(queue.complete())
        _ <- FromFuture[F].apply(queue.watchCompletion())
      } yield {}
    }
}
