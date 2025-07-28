package com.evolution.pekko.conhub

trait ConMetrics[Id, A, M] {

  def onTell(m: M): Unit

  def registerGauges(cons: => Iterable[Conn[A, M]]): Unit

  def onChanged(diff: ConStates.Diff[Id, Conn[A, M]]): Unit
}
