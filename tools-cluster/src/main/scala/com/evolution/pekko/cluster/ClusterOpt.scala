package com.evolution.pekko.cluster

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.cluster.Cluster

object ClusterOpt {
  def apply(system: ActorSystem): Option[Cluster] = {
    if (system hasExtension Cluster) Some(Cluster(system)) else None
  }
}
