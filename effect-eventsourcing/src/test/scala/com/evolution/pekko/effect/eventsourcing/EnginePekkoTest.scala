package com.evolution.pekko.effect.eventsourcing

import cats.effect.*
import cats.effect.implicits.effectResourceOps
import com.evolution.pekko.effect.ActorSuite
import com.evolutiongaming.catshelper.{FromFuture, ToFuture}
import org.apache.pekko.stream.SystemMaterializer

class EnginePekkoTest extends EngineTestCases with ActorSuite {

  override def engine[F[_]: Async: ToFuture: FromFuture, S, E](
    initial: Engine.State[S],
    append: Engine.Append[F, E],
  ): Resource[F, Engine[F, S, E]] =
    for {
      materializer <- Sync[F].delay {
        SystemMaterializer(actorSystem).materializer
      }.toResource
      engine <- Engine.of[F, S, E](initial, materializer, append)
    } yield engine
}
