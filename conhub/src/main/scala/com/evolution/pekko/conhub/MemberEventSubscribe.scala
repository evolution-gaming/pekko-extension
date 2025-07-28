package com.evolution.pekko.conhub

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.{Actor, ActorRefFactory, Props}
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.cluster.ClusterEvent.*

object MemberEventSubscribe extends StrictLogging {

  type Unsubscribe = () => Unit

  def apply(
    cluster: Cluster,
    factor: ActorRefFactory,
    onState: CurrentClusterState => Unit,
    onEvent: MemberEvent => Unit,
  ): Unsubscribe = {

    def actor() = new Actor {
      def receive: Receive = {
        case x: CurrentClusterState => onState(x)
        case x: MemberEvent => onEvent(x)
        case x => logger.warn(s"unexpected $x")
      }
    }

    val props = Props(actor())
    val ref = factor.actorOf(props)
    cluster.subscribe(ref, classOf[MemberEvent])
    () => factor.stop(ref)
  }
}
