package com.evolution.pekko.tools.persistence

import com.evolution.pekko.tools.sharding.ShardEntry

object PersistenceId {
  def apply(persistenceType: String, id: String): String = s"$persistenceType-$id"

  def apply(shardEntry: ShardEntry): String = PersistenceId(
    persistenceType = shardEntry.region.typeName,
    id = shardEntry.id,
  )

  def unapply(persistenceId: String): Option[(String, String)] =
    persistenceId.lastIndexOf("-") match {
      case -1 => None
      case i => Some(persistenceId.take(i) -> persistenceId.drop(i + 1))
    }
}
