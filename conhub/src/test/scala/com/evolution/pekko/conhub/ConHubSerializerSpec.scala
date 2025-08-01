package com.evolution.pekko.conhub

import cats.data.NonEmptyList as Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector

import scala.concurrent.duration.*

class ConHubSerializerSpec extends AnyFunSuite with Matchers {
  import ConHubSerializerSpec.*

  private val serializer = new ConHubSerializer

  private val version = Version.Zero

  test("toBinary & fromBinary for Event.Updated") {
    val value = "value".encodeStr
    val expected = RemoteEvent.Event.Updated(RemoteEvent.Value("id", value, version))
    val actual = toAndFromBinaryEvent(expected)
    actual.value.bytes.decodeStr shouldEqual "value"
    actual.copy(value = actual.value.copy(bytes = value)) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Removed") {
    val expected = RemoteEvent.Event.Removed("id", version)
    toAndFromBinaryEvent(expected) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Disconnected") {
    val expected = RemoteEvent.Event.Disconnected("id", 3.seconds, version)
    toAndFromBinaryEvent(expected) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Sync") {
    val values = Nel.of(1, 2, 3) map { x => x.toString }
    val expected = RemoteEvent.Event.Sync(values map { value => RemoteEvent.Value(value, value.encodeStr, version) })
    val actual = toAndFromBinaryEvent(expected)
    actual.values.toList.foreach { value =>
      value.bytes.decodeStr shouldEqual value.id
    }
    actual.copy(values = expected.values) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Joined") {
    toAndFromBinaryEvent(RemoteEvent.Event.ConHubJoined) shouldEqual RemoteEvent.Event.ConHubJoined
  }

  test("toBinary & fromBinary for Msgs ") {
    val msgs = Nel.of("msg1", "msg2")
    val remoteMsgs = RemoteMsgs(msgs.map(_.encodeStr))
    val actual = toAndFromBinary(remoteMsgs)
    actual.values.map(_.decodeStr) shouldEqual msgs
  }

  private def toAndFromBinaryEvent[A <: RemoteEvent.Event](event: A): A = {
    val remoteEvent = RemoteEvent(event)
    val deserialized = toAndFromBinary(remoteEvent)
    deserialized.event.asInstanceOf[A]
  }

  private def toAndFromBinary[A <: AnyRef](value: A): A = {
    val manifest = serializer.manifest(value)
    val bytes = serializer.toBinary(value)
    val deserialized = serializer.fromBinary(bytes, manifest)
    deserialized.asInstanceOf[A]
  }
}

object ConHubSerializerSpec {

  implicit class ByteVectorOps(val self: ByteVector) extends AnyVal {
    def decodeStr: String = self.decodeUtf8.toTry.get
  }

  implicit class StrOps(val self: String) extends AnyVal {
    def encodeStr: ByteVector = ByteVector.encodeUtf8(self).toTry.get
  }
}
