package com.evolution.pekko.effect.persistence

import cats.Functor

/**
 * Typesafe clone of [[org.apache.pekko.persistence.SnapshotOffer]]
 */
final case class SnapshotOffer[+A](metadata: SnapshotMetadata, snapshot: A)

object SnapshotOffer {

  implicit val functorSnapshotOffer: Functor[SnapshotOffer] = new Functor[SnapshotOffer] {
    def map[A, B](fa: SnapshotOffer[A])(f: A => B) = fa.map(f)
  }

  implicit class SnapshotOfferOps[A](val self: SnapshotOffer[A]) extends AnyVal {
    def map[B](f: A => B): SnapshotOffer[B] = self.copy(snapshot = f(self.snapshot))
  }
}
