package org.apache.pekko.persistence

import com.evolutiongaming.util.RichClass.RichClassOps
import org.apache.pekko.actor.{ActorLogging, DiagnosticActorLogging, StashSupport}
import org.apache.pekko.dispatch.Envelope
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.pattern.FutureRef
import org.apache.pekko.persistence.JournalProtocol.DeleteMessagesTo
import org.apache.pekko.persistence.SnapshotProtocol.DeleteSnapshots

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

trait RecoveryBreaker {
  def onRecoveryFailure(cause: Throwable): Unit
  def onEventRecover(sequenceNr: Long): Unit
}

private class RecoveryBreakerImp(
  saveSnapshotOncePer: Long,
  allowedNumberOfEvents: Long,
  actor: PersistentActor,
  action: => RecoveryBreaker.Action,
  replay: Iterable[Envelope] => Unit,
  replayDelay: FiniteDuration,
) extends RecoveryBreaker {

  import RecoveryBreaker.*
  import actor.context.{dispatcher, system}

  require(
    allowedNumberOfEvents >= saveSnapshotOncePer,
    s"allowedNumberOfEvents < saveSnapshotOncePer, $allowedNumberOfEvents < $saveSnapshotOncePer",
  )

  private var numberOfEvents: Int = 0

  def onRecoveryFailure(cause: Throwable) = cause match {
    case _: BreakRecoveryException =>
    case _ => action match {
        case Action.Clear(timeout) => clearAndReplay(timeout)
        case Action.Stop =>
        case Action.Ignore =>
      }
  }

  def onEventRecover(sequenceNr: Long): Unit = {
    numberOfEvents += 1

    def logMsg =
      s"${ actor.persistenceId }($sequenceNr) recovered $numberOfEvents events, while save-snapshot-once-per=$saveSnapshotOncePer"

    if (numberOfEvents == allowedNumberOfEvents) {
      val msg = s"$logMsg: action: ${ action.getClass.simpleName }"

      def onClear(timeout: FiniteDuration) = {
        clearAndReplay(timeout)
        throw new BreakRecoveryException(msg)
      }

      action match {
        case Action.Clear(timeout) => onClear(timeout)
        case Action.Stop => throw new BreakRecoveryException(msg)
        case Action.Ignore => log.error(msg)
      }
    } else if (numberOfEvents == saveSnapshotOncePer) {
      log.warning(logMsg)
    }
  }

  private def log: LoggingAdapter = actor match {
    case actor: ActorLogging => actor.log
    case actor: DiagnosticActorLogging => actor.log
    case _ => system.log
  }

  private def clearAndReplay(timeout: FiniteDuration): Unit = {
    val persistenceId = actor.persistenceId

    val deleteEvents = {
      val futureRef = FutureRef(system, timeout)
      actor.journal.tell(DeleteMessagesTo(persistenceId, Int.MaxValue, futureRef.ref), futureRef.ref)
      futureRef.future map {
        case x: DeleteMessagesSuccess => x
        case x: DeleteMessagesFailure => x
      }
    }

    val deleteSnapshots = {
      val futureRef = FutureRef(system, timeout)
      actor.snapshotStore.tell(DeleteSnapshots(persistenceId, SnapshotSelectionCriteria()), futureRef.ref)
      futureRef.future map {
        case x: DeleteSnapshotsSuccess => x
        case x: DeleteSnapshotsFailure => x
      }
    }

    val delete = for {
      _ <- deleteEvents
      _ <- deleteSnapshots
    } yield ()

    Await.result(delete, timeout)

    val stashSupport = ReflectValue[StashSupport]("Eventsourced$$internalStash", actor)
    val stash = stashSupport.clearStash()
    system.scheduler.scheduleOnce(replayDelay) {
      replay(stash)
    }
    ()
  }
}

object RecoveryBreaker {

  def apply(
    saveSnapshotOncePer: Long,
    allowedNumberOfEvents: Long,
    actor: PersistentActor,
    action: => RecoveryBreaker.Action,
    replayDelay: FiniteDuration = 300.millis,
  )(
    replay: Iterable[Envelope] => Unit,
  ): RecoveryBreaker = {

    new RecoveryBreakerImp(
      saveSnapshotOncePer = saveSnapshotOncePer,
      allowedNumberOfEvents = allowedNumberOfEvents,
      actor = actor,
      action = action,
      replay = replay,
      replayDelay = replayDelay,
    )
  }

  sealed trait Action
  object Action {
    case object Ignore extends Action
    case object Stop extends Action
    case class Clear(timeout: FiniteDuration = 30.seconds) extends Action
  }

  class BreakRecoveryException(msg: String) extends RuntimeException(msg) with NoStackTrace

  object ReflectValue {
    def apply[T](name: String, instance: AnyRef): T = {
      val fields = instance.getClass.getDeclaredFields
      val field = fields find { _.getName endsWith name } getOrElse { throw new NoSuchFieldException(name) }
      field.setAccessible(true)
      val value = field.get(instance)
      value.asInstanceOf[T]
    }
  }
}
