package com.evolution.pekko.cluster.pubsub

import com.evolution.pekko.serialization.SerializedMsg
import org.apache.pekko.serialization.SerializerWithStringManifest
import scodec.bits.ByteVector
import scodec.codecs.*

import java.io.NotSerializableException

class PubSubSerializer extends SerializerWithStringManifest {
  import PubSubSerializer.*

  private val MsgManifest = "A"

  def identifier: Int = 314261278

  def manifest(x: AnyRef): String =
    x match {
      case _: PubSubMsg => MsgManifest
      case _ => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }

  def toBinary(x: AnyRef) =
    x match {
      case x: PubSubMsg => msgToBinary(x).toByteArray
      case _ => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case MsgManifest => msgFromBinary(ByteVector.view(bytes))
      case _ => notSerializable(s"Cannot deserialize message for manifest $manifest in ${ getClass.getName }")
    }

  private def notSerializable(msg: String) = throw new NotSerializableException(msg)

  private def illegalArgument(msg: String) = throw new IllegalArgumentException(msg)
}

object PubSubSerializer {

  private val codec = int32 ~ int64 ~ utf8_32 ~ variableSizeBytes(int32, bytes)

  private def msgFromBinary(bytes: ByteVector) = {
    val attempt = codec.decode(bytes.bits)
    val identifier ~ timestamp ~ manifest ~ bytes1 = attempt.require.value
    val bytes2 = bytes1
    val serializedMsg = SerializedMsg(identifier, manifest, bytes2)
    PubSubMsg(serializedMsg, timestamp)
  }

  private def msgToBinary(a: PubSubMsg) = {
    val b = a.serializedMsg
    val value = b.identifier ~ a.timestamp ~ b.manifest ~ b.bytes
    codec.encode(value).require
  }
}
