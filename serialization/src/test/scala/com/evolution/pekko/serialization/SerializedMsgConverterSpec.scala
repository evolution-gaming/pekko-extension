package com.evolution.pekko.serialization

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.{SerializationExtension, SerializerWithStringManifest}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.*

class SerializedMsgConverterSpec extends AnyFunSuite with Matchers {

  test("toMsg and fromMsg") {
    val system = ActorSystem(getClass.getSimpleName)
    val converter = SerializedMsgExt(system)
    val serialization = SerializationExtension(system)
    val value = "value"
    val expected = converter.toMsg(value)
    val bytes = serialization.serialize(expected).get
    val serializer = serialization.findSerializerFor(expected).asInstanceOf[SerializerWithStringManifest]
    val manifest = serializer.manifest(expected)
    val identifier = serializer.identifier
    val actual = serialization.deserialize(bytes, identifier, manifest).get.asInstanceOf[SerializedMsg]
    converter.fromMsg(actual).get shouldEqual value
    Await.result(system.terminate(), 3.seconds)
  }
}
