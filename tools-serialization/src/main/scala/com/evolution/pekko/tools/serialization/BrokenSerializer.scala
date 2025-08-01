package com.evolution.pekko.tools.serialization

import org.apache.pekko.serialization.SerializerWithStringManifest

import java.nio.charset.Charset
import scala.util.control.NoStackTrace

class BrokenSerializer extends SerializerWithStringManifest {
  import BrokenSerializer.*

  def identifier = 64574685

  def manifest(x: AnyRef) = x match {
    case x: FailTo => x.getClass.getName
    case _ => sys error s"Unexpected $x"
  }

  def toBinary(x: AnyRef) = x match {
    case x: FailTo.Serialize => throw new FailToSerializeException(x.value)
    case x: FailTo.Deserialize => x.value getBytes Utf8
    case _ => sys error s"Unexpected $x"
  }

  def fromBinary(bytes: Array[Byte], manifest: String) = manifest match {
    case FailTo.Deserialize.Manifest => throw new FailToDeserializeException
    case FailTo.Serialize.Manifest => FailTo.Serialize(new String(bytes, Utf8))
    case _ => sys error s"Unexpected $manifest"
  }
}

object BrokenSerializer {
  lazy val Utf8: Charset = Charset.forName("UTF-8")

  sealed trait FailTo

  object FailTo {
    case class Serialize(value: String = "") extends FailTo
    object Serialize {
      lazy val Manifest: String = classOf[Serialize].getName
    }

    case class Deserialize(value: String = "") extends FailTo
    object Deserialize {
      lazy val Manifest: String = classOf[Deserialize].getName
    }
  }
}

class FailToSerializeException(msg: String) extends RuntimeException(msg) with NoStackTrace
class FailToDeserializeException() extends RuntimeException("") with NoStackTrace
