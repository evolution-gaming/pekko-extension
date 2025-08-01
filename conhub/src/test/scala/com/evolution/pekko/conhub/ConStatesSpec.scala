package com.evolution.pekko.conhub

import com.evolution.pekko.conhub.ConHubSpecHelper.*
import com.evolution.pekko.conhub.ConStates.{Ctx, Diff}
import com.evolution.pekko.conhub.transport.SendMsg
import com.evolution.pekko.tools.test.ActorSpec
import com.evolutiongaming.concurrent.sequentially.{SequentialMap, Sequentially}
import org.apache.pekko.actor.{ActorRef, Address}
import org.apache.pekko.testkit.TestProbe
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

class ConStatesSpec extends AnyWordSpec with ActorSpec with Matchers with ConHubSpecHelper {

  "ConStates" should {

    "disconnect local" in new Scope {
      conStates.update(id, local)
      state shouldEqual Some(local)
      expectUpdated(connection)
      expectDiff(None, Some(local))

      conStates.disconnect(id, version, reconnectTimeout, Ctx.Local)
      expectPublish(RemoteEvent.Event.Disconnected(id, reconnectTimeout, version))
      state shouldEqual Some(disconnected)
    }

    "not disconnect local if localNode = false" in new Scope {
      conStates.update(id, local)
      state shouldEqual Some(local)
      expectUpdated(connection)
      expectDiff(None, Some(local))

      conStates.disconnect(id, version, reconnectTimeout, Ctx.Remote(address))
      state shouldEqual Some(local)
    }

    "disconnect and schedule removal" in new Scope {
      state shouldEqual None

      conStates.disconnect(id, version, reconnectTimeout)
      state shouldEqual None

      conStates.update(id, local)
      state shouldEqual Some(local)
      expectUpdated(connection)
      expectDiff(None, Some(local))

      conStates.disconnect(id, version, reconnectTimeout)
      expectPublish(RemoteEvent.Event.Disconnected(id, reconnectTimeout, version))
      expectDiff(Some(local), Some(disconnected))
      state shouldEqual Some(disconnected)

      expectPublish(RemoteEvent.Event.Removed(id, version))
      expectDiff(Some(disconnected), None)
      states.values.get(id) shouldEqual None

      override def reconnectTimeout = 10.millis
    }

    "update" in new Scope {
      state shouldEqual None

      conStates.update(id, local).get shouldEqual UpdateResult.created
      state shouldEqual Some(local)
      expectUpdated(connection)

      conStates.update(id, local).get shouldEqual UpdateResult(connection)

      val newLocal = local.withConnection(connection.copy(id = "newId"))
      conStates.update(id, newLocal).get shouldEqual UpdateResult(updated = true, connection)
      state shouldEqual Some(newLocal)
      expectUpdated(newLocal.value)

      conStates.update(id, local.copy(version = version.dec)).get shouldEqual UpdateResult(newLocal.value)
      state shouldEqual Some(newLocal)
    }

    "disconnect" in new Scope {
      state shouldEqual None

      conStates.disconnect(id, version, reconnectTimeout).get shouldEqual UpdateResult.empty
      state shouldEqual None

      conStates.update(id, local).get shouldEqual UpdateResult.created
      state shouldEqual Some(local)
      expectUpdated(connection)

      conStates.update(id, version.dec, connection, address).get shouldEqual UpdateResult(connection)

      conStates.update(id, version, connection, address).get shouldEqual UpdateResult(updated = true, connection)
      state shouldEqual Some(remote)

      conStates.disconnect(id, version.dec, reconnectTimeout, Ctx.Remote(address)).get shouldEqual UpdateResult(
        connection,
      )
      state shouldEqual Some(remote)

      conStates.disconnect(id, version, reconnectTimeout, Ctx.Remote(address))
      state shouldEqual Some(disconnected.copy(isLocal = false))

      conStates.update(id, local).get shouldEqual UpdateResult(updated = true, connection)
      state shouldEqual Some(local)
      expectUpdated(connection)
    }

    "remove" in new Scope {
      conStates.remove(id, version).get shouldEqual UpdateResult.empty
      state shouldEqual None

      conStates.update(id, local).get shouldEqual UpdateResult.created
      state shouldEqual Some(local)
      expectUpdated(connection)

      conStates.remove(id, version.dec).get shouldEqual UpdateResult(connection)

      conStates.remove(id, version).get shouldEqual UpdateResult(updated = true, connection)
      state shouldEqual None
    }

    "update remote" in new Scope {
      conStates.update(id, version, connection, address)
      state shouldEqual Some(remote)
    }

    "remove without publish" in new Scope {
      conStates.remove(id, version, Ctx.Remote(address))
      state shouldEqual None

      conStates.update(id, local)
      state shouldEqual Some(local)
      expectUpdated(connection)

      conStates.remove(id, version, Ctx.Remote(address))
      state shouldEqual Some(local)

      cleaState()

      conStates.update(id, version, connection, address)
      state shouldEqual Some(remote)

      conStates.remove(id, version, Ctx.Remote(address))
      state shouldEqual None
    }

    "checkConsistency" in new Scope {
      val past = instant.minus(java.time.Duration.ofDays(1))
      states.put(id, disconnected.copy(timestamp = past))
      conStates.checkConsistency(id)

      expectPublish(RemoteEvent.Event.Removed(id, version))
      expectDiff(Some(disconnected.copy(timestamp = past)), None)
      states.values.get(id) shouldEqual None
    }

    "trigger if reconnected" in new Scope {
      val local1 = local.withConnection(connection.copy(id = "newId"))
      conStates.update(id, local1)
      state shouldEqual Some(local1)
      expectUpdated(local1.value)

      conStates.disconnect(id, version, reconnectTimeout)
      expectPublish(RemoteEvent.Event.Disconnected(id, reconnectTimeout, version))

      val local2 = local1.withSend(new Send)
      conStates.update(id, local2)
      state shouldEqual Some(local2)

      override def reconnectTimeout = 100.millis
    }
  }

  private trait Scope extends ActorScope {
    val id = newId()
    val connection = Connection(id)
    val local = newLocal(connection, new Send)
    val instant = Instant.now() plusSeconds 1.day.toSeconds
    val disconnected = Conn.Disconnected(connection, reconnectTimeout, instant, version = version)
    val address = system.deadLetters.path.address
    val remote = Conn.Remote(connection, address, version)

    val pubSubProbe = TestProbe()

    val states = SequentialMap[Id, Conn[Connection, Msg]](Sequentially.now)

    val connect = (_: ConStates[Id, Connection, Msg]) => {
      val sendMsg = new SendMsg[RemoteEvent] {
        def apply(msg: RemoteEvent, addresses: Iterable[Address]): Unit = pubSubProbe.ref.tell(msg, ActorRef.noSender)
      }
      SendEvent(sendMsg, Serializer.identity[Id], ConnectionSerializer)
    }

    val conStates =
      ConStates(states, 1.minute, system.scheduler, ConnectionSerializer, onStateChanged, () => instant, connect)(
        ExecutionContext.parasitic,
      )

    def onStateChanged(diff: Diff[Id, C]) = {
      testActor ! diff
      Future.unit
    }

    def expectDiff(before: Option[C], after: Option[C]) =
      expectMsg(Diff(connection.id, before, after))

    def state = states.values.get(id)

    def cleaState() = states.remove(id)

    def reconnectTimeout = 1.minute

    def expectPublish(event: RemoteEvent.Event) = pubSubProbe expectMsg RemoteEvent(event)

    def expectUpdated(connection: Connection) =
      pubSubProbe.expectMsgPF() {
        case RemoteEvent(RemoteEvent.Event.Updated(value)) if value.id == id =>
          ConnectionSerializer.from(value.bytes) shouldEqual connection
      }
  }

  implicit class FutureOps[A](self: Future[A]) {
    def get: A = self.value.get.get
  }
}
