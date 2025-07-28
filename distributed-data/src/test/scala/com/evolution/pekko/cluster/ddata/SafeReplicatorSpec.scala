package com.evolution.pekko.cluster.ddata

import cats.effect.unsafe.implicits.global
import cats.effect.{Clock, IO}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.MeasureDuration
import com.evolutiongaming.smetrics.CollectorRegistry
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.ddata.Replicator.{ReadLocal, WriteLocal}
import org.apache.pekko.cluster.ddata.{GCounter, GCounterKey, Replicator as R}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

class SafeReplicatorSpec extends AnyWordSpec with ActorSpec with Matchers {
  import SafeReplicatorSpec.*

  private implicit val readConsistency: R.ReadConsistency = ReadLocal
  private implicit val writeConsistency: R.WriteConsistency = WriteLocal

  "proxy get" in new Scope {
    val result: Future[Option[GCounter]] = replicator.get.unsafeToFuture()
    expectMsg(R.Get(key, readConsistency))
    lastSender ! R.GetSuccess(key, None)(counter)
    result.await shouldEqual Success(Some(counter))
  }

  "proxy get and reply with DataDeleted" in new Scope {
    val result: Future[Option[GCounter]] = replicator.get.unsafeToFuture()
    expectMsg(R.Get(key, readConsistency))
    lastSender ! R.DataDeleted(key, None)
    result.await shouldEqual Failure(ReplicatorError.dataDeleted)
  }

  "proxy get and reply with NotFound" in new Scope {
    val result: Future[Option[GCounter]] = replicator.get.unsafeToFuture()
    expectMsg(R.Get(key, readConsistency))
    lastSender ! R.NotFound(key, None)
    result.await shouldEqual Success(None)
  }

  "proxy get and reply with GetFailure" in new Scope {
    val result: Future[Option[GCounter]] = replicator.get.unsafeToFuture()
    expectMsg(R.Get(key, readConsistency))
    lastSender ! R.GetFailure(key, None)
    result.await shouldEqual Failure(ReplicatorError.getFailure)
  }

  "proxy update" in new Scope {
    val modify: Option[GCounter] => GCounter = _ => counter
    val result: Future[Unit] = replicator.update(modify).unsafeToFuture()
    expectMsg(R.Update(key, writeConsistency, None)(modify))
    lastSender ! R.UpdateSuccess(key, None)
    result.await shouldEqual Success(())
  }

  "proxy update and reply with Failure" in new Scope {
    val modify: Option[GCounter] => GCounter = _ => counter
    val result: Future[Unit] = replicator.update(modify).unsafeToFuture()
    expectMsg(R.Update(key, writeConsistency, None)(modify))
    lastSender ! R.ModifyFailure(key, "errorMsg", failure, None)
    result.await shouldEqual Failure(ReplicatorError.modifyFailure("errorMsg", failure))
  }

  "proxy update and reply with Timeout" in new Scope {
    val modify: Option[GCounter] => GCounter = _ => counter
    val result: Future[Unit] = replicator.update(modify).unsafeToFuture()
    expectMsg(R.Update(key, writeConsistency, None)(modify))
    lastSender ! R.UpdateTimeout(key, None)
    result.await shouldEqual Failure(ReplicatorError.timeout)
  }

  "proxy update and reply with StoreFailure" in new Scope {
    val modify: Option[GCounter] => GCounter = _ => counter
    val result: Future[Unit] = replicator.update(modify).unsafeToFuture()
    expectMsg(R.Update(key, writeConsistency, None)(modify))
    lastSender ! R.StoreFailure(key, None)
    result.await shouldEqual Failure(ReplicatorError.storeFailure)
  }

  "proxy update and reply with Deleted" in new Scope {
    val modify: Option[GCounter] => GCounter = _ => counter
    val result: Future[Unit] = replicator.update(modify).unsafeToFuture()
    expectMsg(R.Update(key, writeConsistency, None)(modify))
    lastSender ! R.DataDeleted(key, None)
    result.await shouldEqual Failure(ReplicatorError.dataDeleted)
  }

  "proxy delete" in new Scope {
    val result: Future[Boolean] = replicator.delete.unsafeToFuture()
    expectMsg(R.Delete(key, writeConsistency, None))
    lastSender ! R.DeleteSuccess(key, None)
    result.await shouldEqual Success(true)
  }

  "proxy delete and reply with AlreadyDeleted" in new Scope {
    val result: Future[Boolean] = replicator.delete.unsafeToFuture()
    expectMsg(R.Delete(key, writeConsistency, None))
    lastSender ! R.DataDeleted(key, None)
    result.await shouldEqual Success(false)
  }

  "proxy delete and reply with ReplicationFailure" in new Scope {
    val result: Future[Boolean] = replicator.delete.unsafeToFuture()
    expectMsg(R.Delete(key, writeConsistency, None))
    lastSender ! R.ReplicationDeleteFailure(key, None)
    result.await shouldEqual Failure(ReplicatorError.replicationFailure)
  }

  "proxy delete and reply with StoreFailure" in new Scope {
    val result: Future[Boolean] = replicator.delete.unsafeToFuture()
    expectMsg(R.Delete(key, writeConsistency, None))
    lastSender ! R.StoreFailure(key, None)
    result.await shouldEqual Failure(ReplicatorError.storeFailure)
  }

  "proxy subscribe" in new Scope {
    val (_, unsubscribe) = replicator.subscribe(().pure[IO], onChanged).allocated.unsafeRunSync()
    val ref: ActorRef = expectMsgPF() { case R.Subscribe(`key`, ref) => ref }
    ref ! R.Changed(key)(counter)
    expectMsg(counter)
    unsubscribe.unsafeRunSync()
    expectMsg(R.Unsubscribe(key, ref))
  }

  "proxy subscribe and stop when data deleted" in new Scope {
    replicator.subscribe(().pure[IO], onChanged).allocated.unsafeRunSync()
    val ref: ActorRef = expectMsgPF() { case R.Subscribe(`key`, ref) => ref }
    ref ! R.DataDeleted(key, None)
    expectMsg(R.Unsubscribe(key, ref))
  }

  "proxy flushChanges" in new Scope {
    replicator.flushChanges.unsafeRunSync()
    expectMsg(R.FlushChanges)
  }

  private trait Scope extends ActorScope {
    val key: GCounterKey = GCounterKey("key")
    val counter: GCounter = GCounter.empty
    val onChanged: GCounter => IO[Unit] = (data: GCounter) => IO { testActor ! data }
    val replicator: SafeReplicator[IO, GCounter] = {
      implicit val measureDuration: MeasureDuration[IO] = MeasureDuration.fromClock(Clock[IO])
      val resource = for {
        metrics <- Metrics.of(CollectorRegistry.empty[IO])
        replicator <- SafeReplicator[IO, GCounter](key, 5.seconds, testActor).withMetrics1(metrics("ddata"), system)
      } yield replicator

      val (replicator, _) = resource.allocated.unsafeRunSync()
      expectMsgType[R.Subscribe[GCounter]].key shouldEqual key
      replicator
    }
  }

}

object SafeReplicatorSpec {

  private val failure = new RuntimeException with NoStackTrace

  implicit class FutureOps[A](val self: Future[A]) extends AnyVal {
    def await: Try[A] = Try { Await.result(self, 3.seconds) }
  }
}
