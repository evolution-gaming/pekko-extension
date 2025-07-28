package org.apache.pekko.persistence

import com.evolution.pekko.test.ActorSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PublicPersistenceSpec extends AnyWordSpec with ActorSpec with Matchers {
  "PublicPersistence" when {
    "journalFor" should {
      "return" in new ActorScope {
        PublicPersistence(system).journalFor("pekko.persistence.journal.inmem") should not be null
      }
    }
    "snapshotStoreFor" should {
      "return" in new ActorScope {
        PublicPersistence(system).snapshotStoreFor("pekko.persistence.snapshot-store.local") should not be null
      }
    }
  }
}
