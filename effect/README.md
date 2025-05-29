# Pekko-Effect
[![Build Status](https://github.com/evolution-gaming/pekko-effect/workflows/CI/badge.svg)](https://github.com/evolution-gaming/pekko-effect/actions?query=workflow%3ACI) 
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/pekko-effect/badge.svg)](https://coveralls.io/r/evolution-gaming/pekko-effect)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/addac6520ff440acb0b2d3a05b37740b)](https://app.codacy.com/gh/evolution-gaming/pekko-effect/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Version](https://img.shields.io/badge/version-click-blue)](https://evolution.jfrog.io/artifactory/api/search/latestVersion?g=com.evolution&a=pekko-effect-actor_2.13&repos=public)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

This project aims to build a bridge between [Pekko](https://pekko.apache.org/docs/pekko) and pure functional code based 
on [cats-effect](https://typelevel.org/cats-effect).

It is forked from [akka-effect](https://github.com/evolution-gaming/akka-effect) at v4.1.10 by replacing `akka` with `pekko`.

Covered ("classic", not the "typed" kind of actors!):
* [Actors](https://pekko.apache.org/docs/pekko/current/actors.html)
* [Persistence](https://pekko.apache.org/docs/pekko/current/persistence.html)

## Building blocks


### `pekko-effect-actor` module 

#### [Tell.scala](actor/src/main/scala/com/evolution/pekkoeffect/Tell.scala)

Represents `ActorRef.tell`

```scala
trait Tell[F[_], -A] {

  def apply(a: A, sender: Option[ActorRef] = None): F[Unit]
}
```


#### [Ask.scala](actor/src/main/scala/com/evolution/pekkoeffect/Ask.scala)

Represents `ActorRef.ask` pattern

```scala
trait Ask[F[_], -A, B] {

  def apply(msg: A, timeout: FiniteDuration, sender: Option[ActorRef]): F[B]
}
```


#### [Reply.scala](actor/src/main/scala/com/evolution/pekkoeffect/Reply.scala)

Represents reply pattern: `sender() ! reply`

```scala
trait Reply[F[_], -A] {

  def apply(msg: A): F[Unit]
}
```


#### [Receive.scala](actor/src/main/scala/com/evolution/pekkoeffect/Receive.scala)

This is what you need to implement instead of familiar `new Actor { ... }`  

```scala
trait Receive[F[_], -A, B] {

  def apply(msg: A): F[B]

  def timeout:  F[B]
}
```


#### [ActorOf.scala](actor/src/main/scala/com/evolution/pekkoeffect/ActorOf.scala)

Constructs `Actor.scala` out of `receive: ActorCtx[F] => Resource[F, Receive[F, Any]]`


#### [ActorCtx.scala](actor/src/main/scala/com/evolution/pekkoeffect/ActorCtx.scala)

Wraps `ActorContext`

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


### `pekko-effect-persistence` module

#### [PersistentActorOf.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/PersistentActorOf.scala)

Constructs `PersistentActor.scala` out of `eventSourcedOf: ActorCtx[F] => F[EventSourced[F, S, E, C]]`


#### [EventSourced.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/EventSourced.scala)

Describes a lifecycle of entity with regard to event sourcing, phases are: Started, Recovering, Receiving and Termination

```scala
trait EventSourced[F[_], S, E, C] {

  def eventSourcedId: EventSourcedId

  def recovery: Recovery

  def pluginIds: PluginIds

  def start: Resource[F, RecoveryStarted[F, S, E, C]]
}
```

#### [RecoveryStarted.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/RecoveryStarted.scala)

Describes start of recovery phase
 
```scala
trait RecoveryStarted[F[_], S, E, C] {

  def apply(
    seqNr: SeqNr,
    snapshotOffer: Option[SnapshotOffer[S]]
  ): Resource[F, Recovering[F, S, E, C]]
}
```


#### [Recovering.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/Recovering.scala)

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


#### [Replay.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/Replay.scala)

Used during recovery to replay events
 
```scala
trait Replay[F[_], A] {

  def apply(seqNr: SeqNr, event: A): F[Unit]
}
```


#### [Journaller.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/Journaller.scala)

Describes communication with underlying journal

```scala
trait Journaller[F[_], -A] {

  def append: Append[F, A]

  def deleteTo: DeleteEventsTo[F]
}
```


#### [Snapshotter.scala](persistence/src/main/scala/com/evolution/pekkoeffect/persistence/Snapshotter.scala)

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


### `pekko-effect-eventsourced` module

#### [Engine.scala](eventsourcing/src/main/scala/com/evolution/pekkoeffect/eventsourcing/Engine.scala)

This is the main runtime/queue where all actions against your state are processed in desired eventsourcing sequence:
1. validate and finalize events
2. append events to journal
3. publish changed state
4. execute side effects

It is optimised for maximum throughput hence different steps of different actions might be executed in parallel as well as events might be stored in batches

```scala
trait Engine[F[_], S, E] {

  def state: F[State[S]]

  /**
    * @return Outer F[_] is about `load` being enqueued, this immediately provides order guarantees
    *         Inner F[_] is about `load` being completed
    */
  def apply[A](load: F[Validate[F, S, E, A]]): F[F[A]]
}
```


## Setup

in [`build.sbt`](https://www.scala-sbt.org/1.x/docs/Basic-Def.html#What+is+a+build+definition%3F)
```scala
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-actor" % "0.2.1"

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-actor-tests" % "0.2.1"

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-persistence" % "0.2.1"

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-eventsourcing" % "0.2.1"

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-cluster" % "0.2.1"

libraryDependencies += "com.evolutiongaming" %% "pekko-effect-cluster-sharding" % "0.2.1"
```