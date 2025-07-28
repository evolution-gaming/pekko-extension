[![Build Status](https://github.com/evolution-gaming/pekko-extension/workflows/CI/badge.svg)](https://github.com/evolution-gaming/pekko-extension/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/github/evolution-gaming/pekko-extension/badge.svg?branch=main)](https://coveralls.io/github/evolution-gaming/pekko-extension?branch=main)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/2a147aa5b283460c8c6ab43bac4c787e)](https://app.codacy.com/gh/evolution-gaming/pekko-extension/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Version](https://img.shields.io/badge/version-click-blue)](https://evolution.jfrog.io/artifactory/api/search/latestVersion?g=com.evolution&a=pekko-extension_2.13&repos=public)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

# Pekko Extension libraries

Set of extension libraries for [`pekko`](https://pekko.apache.org/).

## Getting Started

All libraries require the same initial setup, like:
```scala
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")
```
Setting dependency:
```scala
libraryDependencies += "com.evolution" %% "pekko-extension-<name>" % "<version>"
```

## Extensions

### pekko-extension-serialization

TODO add description!

### pekko-extension-pubsub

Typesafe layer for `DistributedPubSubMediator`.

```scala
trait PubSub[F[_]] {

  def publish[A: Topic: ToBytes](
    msg: A,
    sender: Option[ActorRef] = None,
    sendToEachGroup: Boolean = false
  ): F[Unit]

  def subscribe[A: Topic: FromBytes: ClassTag](
    group: Option[String] = None)(
    onMsg: OnMsg[F, A]
  ): Resource[F, Unit]

  def topics(timeout: FiniteDuration = 3.seconds): F[Set[String]]
}
```

For an ability to serialize/deserialize messages to offload `pekko` remoting and improve throughput, 
check [`DistributedPubSubMediatorSerializing.scala`](src/main/scala/org/apache/pekko/cluster/pubsub/DistributedPubSubMediatorSerializing.scala).

### set of `pekko-extension-test` libraries

These two libraries were created to provide a set of tests to be used in projects dependent on [Pekko](https://pekko.apache.org)
libraries.
For instance, to prevent the following "surprise" at runtime:

```
java.lang.IllegalStateException: You are using version 1.2.0 of Pekko HTTP, but it appears you (perhaps indirectly) also depend on older versions of related artifacts. You can solve this by adding an explicit dependency on version 1.2.0 of the [pekko-http, pekko-http-testkit] artifacts to your project. Here's a complete collection of detected artifacts: (1.1.0, [pekko-http, pekko-http-testkit]), (1.2.0, [pekko-http-core, pekko-parsing]). See also: https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed
	at org.apache.pekko.util.ManifestInfo.checkSameVersion(ManifestInfo.scala:188)
	at org.apache.pekko.util.ManifestInfo.checkSameVersion(ManifestInfo.scala:166)
	at org.apache.pekko.http.scaladsl.HttpExt.<init>(Http.scala:89)
	at org.apache.pekko.http.scaladsl.Http$.createExtension(Http.scala:1140)
	at org.apache.pekko.http.scaladsl.Http$.createExtension(Http.scala:871)
	at org.apache.pekko.actor.ActorSystemImpl.registerExtension(ActorSystem.scala:1175)
	at org.apache.pekko.actor.ExtensionId.apply(Extension.scala:87)
	at org.apache.pekko.actor.ExtensionId.apply$(Extension.scala:86)
```

#### pekko-extension-test-actor

For [pekko-actor](https://pekko.apache.org/docs/pekko/current/) tests that all `pekko` modules are of the same version.

Set up the dependency in `Test` scope:
```scala
libraryDependencies += "com.evolution" %% "pekko-extension-test-actor" % "<version>" % Test
```

And add the following test into your project:
```scala
import com.evolution.pekkotest.PekkoActorSuite

class PekkoActorTest extends PekkoActorSuite
```

#### pekko-extension-test-http

For [pekko-http](https://pekko.apache.org/docs/pekko-http/current/) tests that all `pekko-http` modules are of the same version.

Set up the dependency in `Test` scope:
```scala
libraryDependencies += "com.evolution" %% "pekko-extension-test-http" % "<version>" % Test
```

And add the following test into your project.
```scala
import com.evolution.pekkotest.PekkoHttpSuite

class PekkoHttpTest extends PekkoHttpSuite
```

### pekko-extension-distributed-data-tools

`SafeReplicator` is a typesafe api for [Distributed Data replicator](https://pekko.apache.org/docs/pekko/current/typed/distributed-data.html)

```scala
trait SafeReplicator[F[_], A <: ReplicatedData] {

  def get(implicit consistency: ReadConsistency): F[Option[A]]

  def update(modify: Option[A] => A)(implicit consistency: WriteConsistency): F[Unit]

  def delete(implicit consistency: WriteConsistency): F[Boolean]

  def subscribe(
    onStop: F[Unit],
    onChanged: A => F[Unit])(implicit
    factory: ActorRefFactory,
    executor: ExecutionContext
  ): Resource[F, Unit]

  def flushChanges: F[Unit]
}
```

### pekko-extension-sharding-strategy

Alternative to [org.apache.pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy](https://github.com/apache/pekko/blob/main/cluster-sharding/src/main/scala/org/apache/pekko/cluster/sharding/ShardCoordinator.scala#L78).

#### Api

```scala
trait ShardingStrategy[F[_]] {

  def allocate(requester: Region, shard: Shard, current: Allocation): F[Option[Region]]

  def rebalance(current: Allocation, inProgress: Set[Shard]): F[List[Shard]]
}
```

#### Syntax

```scala
val strategy = LeastShardsStrategy()
  .filterShards(...)
  .filterRegions(...)
  .rebalanceThreshold(10)
  .takeShards(10) 
  .shardRebalanceCooldown(1.minute)
  .logging(...)
  .toAllocationStrategy()
```

### set of `pekko-extension-tools` libraries 

#### pekko-extension-tools-test

TODO add description!

#### pekko-extension-tools-util

TODO add description!

#### pekko-extension-tools-serialization

TODO add description!

#### pekko-extension-tools-persistence

TODO add description!

#### pekko-extension-tools-cluster

TODO add description!

#### pekko-extension-tools-instrumentation

TODO add description!

TODO do we need umbrella lib `pekko-extension-tools`?

### pekko-extension-conhub

ConHub is a distributed registry used to manage websocket connections on the different nodes of an application.
It enables you to send a serializable message to one or many connections hiding away the complexity of distributed system.
In short: user provides `lookup` criteria and a `message` and `conHub` does the job routing message to physical 
instances of a matched connections

Usage example:
```scala
type Connection = ??? // type representing physical connection
final case class Msg(bytes: Array[Byte]) // serializable
final case class Envelope(lookup: LookupById, msg: Msg)
final case class LookupById(id: String)
val conHub: ConHub[String, LookupById, Connection, Envelope] = ???
conHub ! Envelope(LookupById("testId"), Msg(Array(…)))
```

# set of `pekko-extension-effect` libraries

This project aims to build a bridge between [Pekko](https://pekko.apache.org/docs/pekko) and pure functional code based
on [cats-effect](https://typelevel.org/cats-effect).

#### pekko-extension-effect-actor

Covered ("classic", not the "typed" kind of actors!):
* [Actors](https://pekko.apache.org/docs/pekko/current/actors.html)
* [Persistence](https://pekko.apache.org/docs/pekko/current/persistence.html)

##### [Tell.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/Tell.scala)

Represents `ActorRef.tell`:
```scala
trait Tell[F[_], -A] {
  def apply(a: A, sender: Option[ActorRef] = None): F[Unit]
}
```

##### [Ask.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/Ask.scala)

Represents `ActorRef.ask` pattern:
```scala
trait Ask[F[_], -A, B] {
  def apply(msg: A, timeout: FiniteDuration, sender: Option[ActorRef]): F[B]
}
```

##### [Reply.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/Reply.scala)

Represents a reply pattern: `sender() ! reply`:
```scala
trait Reply[F[_], -A] {
  def apply(msg: A): F[Unit]
}
```

##### [Receive.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/Receive.scala)

This is what you need to implement instead of familiar `new Actor { ... }`:
```scala
trait Receive[F[_], -A, B] {
  def apply(msg: A): F[B]
  def timeout:  F[B]
}
```

##### [ActorOf.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/ActorOf.scala)

Constructs `Actor.scala` out of `receive: ActorCtx[F] => Resource[F, Receive[F, Any]]`.

##### [ActorCtx.scala](effect-actor/src/main/scala/com/evolution/pekko/effect/ActorCtx.scala)

Wraps `ActorContext`:
```scala
trait ActorCtx[F[_]] {
  def self: ActorRef
  def parent: ActorRef
  def executor: ExecutionContextExecutor
  def setReceiveTimeout(timeout: Duration): F[Unit]
  def child(name: String): F[Option[ActorRef]]
  def children: F[List[ActorRef]]
  def actorRefFactory: ActorRefFactory
  def watch[A](actorRef: ActorRef, msg: A): F[Unit]
  def unwatch(actorRef: ActorRef): F[Unit]
  def stop: F[Unit]
}
```

#### pekko-extension-effect-persistence

##### [PersistentActorOf.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/PersistentActorOf.scala)

Constructs `PersistentActor.scala` out of `eventSourcedOf: ActorCtx[F] => F[EventSourced[F, S, E, C]]`


##### [EventSourced.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/EventSourced.scala)

Describes a lifecycle of entity with regard to event sourcing, phases are: Started, Recovering, Receiving and Termination

```scala
trait EventSourced[F[_], S, E, C] {
  def eventSourcedId: EventSourcedId
  def recovery: Recovery
  def pluginIds: PluginIds
  def start: Resource[F, RecoveryStarted[F, S, E, C]]
}
```

##### [RecoveryStarted.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/RecoveryStarted.scala)

Describes the start of the recovery phase

```scala
trait RecoveryStarted[F[_], S, E, C] {
  def apply(
    seqNr: SeqNr,
    snapshotOffer: Option[SnapshotOffer[S]]
  ): Resource[F, Recovering[F, S, E, C]]
}
```


##### [Recovering.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/Recovering.scala)

Describes recovery phase

```scala
trait Recovering[F[_], S, E, C] {
  def replay: Resource[F, Replay[F, E]]

  def completed(
    seqNr: SeqNr,
    journaller: Journaller[F, E],
    snapshotter: Snapshotter[F, S]
  ): Resource[F, Receive[F, C]]
}
```


##### [Replay.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/Replay.scala)

Used during recovery to replay events

```scala
trait Replay[F[_], A] {
  def apply(seqNr: SeqNr, event: A): F[Unit]
}
```


##### [Journaller.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/Journaller.scala)

Describes communication with underlying journal

```scala
trait Journaller[F[_], -A] {
  def append: Append[F, A]
  def deleteTo: DeleteEventsTo[F]
}
```


##### [Snapshotter.scala](effect-persistence/src/main/scala/com/evolution/pekko/effect/persistence/Snapshotter.scala)

Describes communication with underlying snapshot storage

```scala
/**
  * Describes communication with underlying snapshot storage
  *
  * @tparam A - snapshot
  */
trait Snapshotter[F[_], -A] {
  def save(seqNr: SeqNr, snapshot: A): F[F[Instant]]
  def delete(seqNr: SeqNr): F[F[Unit]]
  def delete(criteria: SnapshotSelectionCriteria): F[F[Unit]]
}
```

#### pekko-extension-effect-testkit

TODO add description!

#### pekko-extension-effect-actor-tests

TODO add description! 

#### pekko-extension-effect-persistence-api

TODO add description!

#### pekko-extension-effect-persistence

TODO add description!

#### pekko-extension-effect-cluster

TODO add description!

#### pekko-extension-effect-cluster-sharding

TODO add description! 

#### pekko-extension-effect-eventsourcing

[Engine.scala](effect-eventsourcing/src/main/scala/com/evolution/pekko/effect/eventsourcing/Engine.scala)

This is the main runtime/queue where all actions against your state are processed in a desired event-sourcing sequence:
1. validate and finalize events
2. append events to journal
3. publish changed state
4. execute side effects

It is optimized for maximum throughput, hence different steps of different actions might be executed in parallel as well as events 
might be stored in batches

```scala
trait Engine[F[_], S, E] {
  def state: F[State[S]]

  /**
    * @return Outer F[_] is about `load` being enqueued, this immediately provides order guarantees
    *         Inner F[_] is about a `load` being completed
    */
  def apply[A](load: F[Validate[F, S, E, A]]): F[F[A]]
}
```

## Library mappings `pekko` to `akka` 

| pekko                                   | akka                                                                         | migrated from version |
|-----------------------------------------|------------------------------------------------------------------------------|-----------------------|
| pekko-extension-serialization           | [akka-serialization](https://github.com/evolution-gaming/akka-serialization) | 1.1.0                 |
| pekko-extension-pubsub                  | [pubsub](https://github.com/evolution-gaming/akka-pubsub)                    | 10.0.0                |
| pekko-extension-test-actor              | [akka-test](https://github.com/evolution-gaming/akka-test)                   | 0.3.0                 |
| pekko-extension-test-http               | [akka-test](https://github.com/evolution-gaming/akka-test)                   | 0.3.0                 |
| pekko-extension-distributed-data-tools  | [ddata-tools](https://github.com/evolution-gaming/ddata-tools/)              | 3.1.0                 |
| pekko-extension-sharding-strategy       | [sharding-strategy](https://github.com/evolution-gaming/sharding-strategy)   | 3.0.2                 |
| pekko-extension-tools-test              | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-tools-util              | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-tools-serialization     | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-tools-persistence       | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-tools-cluster           | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-tools-instrumentation   | [akka-tools](https://github.com/evolution-gaming/akka-tools/)                | 3.3.13                |
| pekko-extension-conhub                  | [conhub](https://github.com/evolution-gaming/conhub)                         | 3.0.0                 |
| pekko-extension-effect-actor            | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-testkit          | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-actor-tests      | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-persistence-api  | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-persistence      | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-cluster          | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-cluster-sharding | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
| pekko-extension-effect-eventsourcing    | [akka-effect](https://github.com/evolution-gaming/akka-effect)               | 4.1.10                |
