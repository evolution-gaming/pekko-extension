package com.evolution.pekko.effect.actor

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.evolution.pekko.effect.actor.ActorVar.Directive
import com.evolution.pekko.effect.actor.Fail.implicits.*
import com.evolutiongaming.catshelper.CatsHelper.*
import com.evolutiongaming.catshelper.ToFuture
import org.apache.pekko.actor.{Actor, ActorRef, ReceiveTimeout}

/**
 * Creates instance of [[org.apache.pekko.actor.Actor]] out of [[ReceiveOf]]
 */
object ActorOf {

  type Stop = Boolean

  def apply[F[_]: Async: ToFuture](
    receiveOf: ReceiveOf[F, Envelope[Any], Stop],
  ): Actor = {

    type State = Receive[F, Envelope[Any], Stop]

    def onPreStart(
      actorCtx: ActorCtx[F],
    )(implicit
      fail: Fail[F],
    ) =
      receiveOf(actorCtx)
        .handleErrorWith { (error: Throwable) =>
          s"failed to allocate receive".fail[F, State](error).toResource
        }

    def onReceive(
      a: Any,
      sender: ActorRef,
    )(implicit
      fail: Fail[F],
    ) = { (state: State) =>
      val stop = a match {
        case ReceiveTimeout => state.timeout
        case a => state(Envelope(a, sender))
      }
      stop
        .map {
          case false => Directive.ignore[Releasable[F, State]]
          case true => Directive.stop[Releasable[F, State]]
        }
        .handleErrorWith { error =>
          s"failed on $a from $sender".fail[F, Directive[Releasable[F, State]]](error)
        }
    }

    new Actor {

      private implicit val fail: Fail[F] = Fail.fromActorRef[F](self)

      private val act = Act.Adapter(self)

      private val actorVar = ActorVar[F, State](act.value, context)

      override def preStart(): Unit = {
        super.preStart()
        act.sync {
          val actorCtx = ActorCtx[F](act.value, context)
          actorVar.preStart {
            onPreStart(actorCtx)
          }
        }
      }

      def receive: Receive = act.receive {
        case a => actorVar.receive(onReceive(a, sender = sender()))
      }

      override def postStop(): Unit = {
        act.value.postStop()
        act.sync {
          actorVar.postStop().toFuture
        }
        super.postStop()
      }
    }
  }
}
