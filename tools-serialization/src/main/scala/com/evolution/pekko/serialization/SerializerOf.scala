package com.evolution.pekko.serialization

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.SerializationExtension

import scala.reflect.ClassTag

object SerializerOf {
  def apply[T <: org.apache.pekko.serialization.Serializer](
    system: ActorSystem,
  )(implicit
    tag: ClassTag[T],
  ): T = {
    val config = system.settings.config.getConfig("pekko.actor.serialization-identifiers")
    val name = tag.runtimeClass.getName
    val identifier = config.getInt(s""""$name"""")
    val serializer = SerializationExtension(system).serializerByIdentity(identifier)
    serializer.asInstanceOf[T]
  }
}
