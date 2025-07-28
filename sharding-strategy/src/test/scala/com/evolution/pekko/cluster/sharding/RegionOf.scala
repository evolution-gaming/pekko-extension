package com.evolution.pekko.cluster.sharding

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props}

object RegionOf {

  def apply(actorSystem: ActorSystem): ActorRef = {
    def actor(): Actor = new Actor {
      def receive: Actor.Receive = PartialFunction.empty
    }

    val props = Props(actor())
    actorSystem.actorOf(props)
  }
}
