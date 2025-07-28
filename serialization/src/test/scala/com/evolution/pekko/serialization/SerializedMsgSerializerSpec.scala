package com.evolution.pekko.serialization

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector

class SerializedMsgSerializerSpec extends AnyFunSuite with Matchers {

  test("toBinary & fromBinary for SerializedMsg") {
    val bytes = "bytes"
    val expected = SerializedMsg(1, "manifest", ByteVector.encodeUtf8(bytes).toTry.get)
    val actual = toAndFromBinary(expected)
    actual.identifier shouldEqual actual.identifier
    actual.manifest shouldEqual actual.manifest
    actual.bytes.decodeUtf8 shouldEqual Right(bytes)
  }

  def toAndFromBinary(msg: SerializedMsg): SerializedMsg = {
    val bytes = SerializedMsgSerializer.toBinary(msg)
    SerializedMsgSerializer.fromBinary(bytes)
  }
}
