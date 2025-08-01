package com.evolution.pekko.conhub

import cats.data.NonEmptyList as Nel
import com.evolutiongaming.concurrent.FutureHelper.*
import com.evolutiongaming.concurrent.sequentially.Sequentially
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object ConHubImpl extends LazyLogging {

  type OnMsgs[M] = Nel[M] => Unit

  type Connect[Id, T, L, M] = OnMsgs[M] => (SearchEngine[Id, T, M, L], ConStates[Id, T, M], SendMsgs[Id, T, M])

  def apply[Id, A, L, K, M](
    sequentiallyLocal: Sequentially[K],
    sequentiallyRemote: Sequentially[K],
    msgOps: ConHubImpl.MsgOps[M, L, K],
    metrics: ConMetrics[Id, A, M],
    connect: Connect[Id, A, L, M],
    executor: ExecutionContext,
  ): ConHub[Id, A, M, L] = {

    implicit val executor1: ExecutionContext = executor

    new ConHub[Id, A, M, L] {

      private val initialized = new AtomicBoolean(false)

      val onMsgs: OnMsgs[M] = msgs =>
        if (initialized.get()) {
          // there is no silver bullet:
          // 1. either we fix race condition within batch
          // 2. or fix race condition between keys from different batches
          // I prefer second…

          // Future.traverseSequentially(msgs.toList) { msg =>
          msgs.toList.foreach { msg =>
            def send() = {
              val cons = this.cons(msg.lookup, localCall = false)
              if (cons.nonEmpty) {
                sendMsgs.local(msg, cons, remote = true)
              }
            }

            msg.key match {
              case None => Future(send()) // to not block current actor
              case Some(key) => sequentiallyRemote(key)(send())
            }
          }
        }

      val (searchEngine, conStates, sendMsgs) = connect(onMsgs)

      initialized.set(true)

      metrics.registerGauges(cons)

      def !(msg: M): SR = {
        logAndMeter(msg)

        def execute = {
          val lookup = msg.lookup
          val cons = this.cons(lookup)

          if (cons.nonEmpty) {
            sendMsgs.local(msg, cons, remote = false)
            val addresses = this.addresses(cons).toSet
            if (addresses.nonEmpty) sendMsgs.remote(Nel.one(msg), addresses)
          }
          SendResult(cons)
        }

        msg.key match {
          case None => execute.future
          case Some(key) => sequentiallyLocal(key)(execute)
        }
      }

      def !(msgs: Nel[M]): SR = {
        for { msg <- msgs.toList } logAndMeter(msg)

        val msgsAndCons = for {
          msg <- msgs.toList
          cons = this.cons(msg.lookup)
          if cons.nonEmpty
        } yield (msg, cons)

        val future = Nel.fromList(msgsAndCons) match {
          case Some(msgsAndCons) =>
            Future.traverseSequentially(msgsAndCons.toList) {
              case (msg, connections) =>
                def send() = sendMsgs.local(msg, connections, remote = false)

                msg.key match {
                  case None => send().future
                  case Some(key) => sequentiallyLocal(key)(send())
                }
            }
          case None =>
            Future.unit
        }

        val remoteMsgs = for {
          (msg, cons) <- msgsAndCons if addresses(cons).nonEmpty
        } yield msg

        for { remoteMsgs <- Nel.fromList(remoteMsgs) } sendMsgs.remote(remoteMsgs, Nil)

        val cons = msgsAndCons.flatMap { case (_, cons) => cons }

        future map { _ => SendResult(cons) }
      }

      def update(
        id: Id,
        version: Version,
        con: A,
        send: Conn.Send[M],
      ): Result =
        conStates.update(id, Conn.Local(con, send, version))

      def disconnect(id: Id, version: Version, reconnectTimeout: FiniteDuration): Result =
        conStates.disconnect(id, version, reconnectTimeout)

      def remove(id: Id, version: Version): Result =
        conStates.remove(id, version)

      private def addresses(cons: Iterable[C]) =
        cons.collect { case c: C.Remote => c.address }

      private implicit class MsgOpsProxy(self: M) {
        def key: Option[K] = msgOps key self
        def lookup: L = msgOps lookup self
      }

      private def logAndMeter(msg: M) = {
        logger.debug(s"<<< ${ msg.toString take 1000 }")
        metrics.onTell(msg)
      }
    }
  }

  trait MsgOps[A, L, K] {
    def lookup(x: A): L
    def key(x: A): Option[K]
  }
}
