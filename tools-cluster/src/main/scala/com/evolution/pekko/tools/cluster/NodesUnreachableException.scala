package com.evolution.pekko.tools.cluster

import cats.data.NonEmptyList as Nel
import org.apache.pekko.actor.{ActorSystem, Address}
import org.apache.pekko.cluster.Cluster

import scala.concurrent.TimeoutException

class NodesUnreachableException(addresses: Nel[Address], cause: Throwable) extends TimeoutException {

  override def getCause: Throwable = cause

  override def getMessage: String = addresses match {
    case Nel(node, Nil) => s"node $node is unreachable"
    case nodes => s"nodes ${ nodes.toList mkString ", " } are unreachable"
  }
}

object NodesUnreachableException {

  def opt(
    timeoutException: TimeoutException,
    system: ActorSystem,
    role: Option[String] = None,
  ): Option[NodesUnreachableException] = {

    def unreachableAddresses(cluster: Cluster, role: Option[String]) =
      cluster.state.unreachable.collect { case m if role.forall(m.roles.contains) => m.address }

    for {
      cluster <- ClusterOpt(system)
      unreachable <- Nel.fromList(unreachableAddresses(cluster, role).toList)
    } yield new NodesUnreachableException(unreachable, timeoutException)

  }
}
