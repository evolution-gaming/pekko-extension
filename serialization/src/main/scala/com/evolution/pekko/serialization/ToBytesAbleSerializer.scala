package com.evolution.pekko.serialization

import org.apache.pekko.serialization.SerializerWithStringManifest
import scodec.bits.ByteVector

import java.io.NotSerializableException

class ToBytesAbleSerializer extends SerializerWithStringManifest {

  private val Manifest = "A"

  def identifier: Int = 59156265

  def manifest(x: AnyRef): String =
    x match {
      case _: ToBytesAble => Manifest
      case _ => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }

  def toBinary(x: AnyRef): Array[Byte] =
    x match {
      case x: ToBytesAble => x.bytes.toArray
      case _ => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case Manifest => ToBytesAble.Bytes(ByteVector.view(bytes))
      case _ => notSerializable(s"Cannot deserialize message for manifest $manifest in ${ getClass.getName }")
    }

  private def notSerializable(msg: String): Nothing = throw new NotSerializableException(msg)

  private def illegalArgument(msg: String): Nothing = throw new IllegalArgumentException(msg)
}
