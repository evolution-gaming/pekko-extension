package com.evolution.pekkoeffect.persistence

import java.time.Instant

/** @see
  *   [[org.apache.pekko.persistence.SnapshotMetadata]]
  */
final case class SnapshotMetadata(seqNr: SeqNr, timestamp: Instant)

object SnapshotMetadata {

  val Empty: SnapshotMetadata = SnapshotMetadata(seqNr = 1, timestamp = Instant.ofEpochMilli(0))

  def apply(metadata: org.apache.pekko.persistence.SnapshotMetadata): SnapshotMetadata =
    SnapshotMetadata(seqNr = metadata.sequenceNr, timestamp = Instant.ofEpochMilli(metadata.timestamp))
}
