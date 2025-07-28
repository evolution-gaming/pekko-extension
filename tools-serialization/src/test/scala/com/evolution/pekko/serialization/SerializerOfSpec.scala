package com.evolution.pekko.serialization

import com.evolution.test.ActorSpec
import org.apache.pekko.serialization.ByteArraySerializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SerializerOfSpec extends AnyWordSpec with Matchers with ActorSpec {
  "SerializerOf" should {
    "find serializer of type" in new ActorScope {
      val serializer = SerializerOf[ByteArraySerializer](system)
      serializer should not be null
    }
  }
}
