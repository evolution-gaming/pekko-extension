package com.evolution.pekko.effect.actor

import cats.syntax.all.*

final case class ActorError(
  msg: String,
  cause: Option[Throwable] = None,
) extends RuntimeException(msg, cause.orNull)

object ActorError {

  def apply(msg: String, cause: Throwable): ActorError = ActorError(msg, cause.some)
}
