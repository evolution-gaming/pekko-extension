package org.apache.pekko.persistence

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.persistence.JournalProtocol.*
import org.apache.pekko.persistence.SnapshotProtocol.*

object Replicate {
  def opt(x: Any, ref: ActorRef): Option[Any] = PartialFunction.condOpt(x) {
    case x: DeleteMessagesTo => x.copy(persistentActor = ref)
    case x: WriteMessages => x.copy(persistentActor = ref)
    case x: SaveSnapshot => x
    case x: DeleteSnapshot => x
    case x: DeleteSnapshots => x
  }
}
