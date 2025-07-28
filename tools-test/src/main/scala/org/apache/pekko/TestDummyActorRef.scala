package org.apache.pekko

import org.apache.pekko.actor.*

class TestDummyActorRef(val path: ActorPath) extends MinimalActorRef {
  def provider: ActorRefProvider = null
}
