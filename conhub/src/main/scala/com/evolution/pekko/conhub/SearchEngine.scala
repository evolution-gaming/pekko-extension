package com.evolution.pekko.conhub

import scala.concurrent.Future

/**
 * @tparam Id
 *   type of connection id
 * @tparam A
 *   type of data contained by the connection
 * @tparam M
 *   type of message
 * @tparam L
 *   type of lookup used to find the connection
 */
trait SearchEngine[Id, A, M, L] extends ConnTypes[A, M] {

  type Cons = collection.Map[Id, C]

  /**
   * @param localCall
   *   true when the origin of search is on the current node, false otherwise. might be used to find
   *   a remote connection regarding the origin of call
   */
  def apply(l: L, cons: Cons, localCall: Boolean): Iterable[C]

  /**
   * Triggered when the state of connection being updated
   *
   * @param diff
   *   contains state of connection before and after the update
   * @param cons
   *   list of all connections
   */
  def update(diff: ConStates.Diff[Id, C], cons: Cons): Future[Unit]
}
