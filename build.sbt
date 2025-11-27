import Dependencies.*

val commonSettings = Seq(
  organization := "com.evolution",
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  homepage := Some(url("https://github.com/evolution-gaming/pekko-extension")),
  startYear := Some(2016),
  crossScalaVersions := Seq("2.13.18", "3.3.7"),
  scalaVersion := crossScalaVersions.value.head,
  scalacOptions ++= Seq(
    "-release:17",
    "-deprecation",
  ),
  scalacOptions ++= crossSettings(
    scalaVersion = scalaVersion.value,
    if2 = Seq(
      "-Xsource:3",
    ),
    // Good compiler options for Scala 2.13 are coming from com.evolution:sbt-scalac-opts-plugin:0.0.9,
    // but its support for Scala 3 is limited, especially what concerns linting options.
    //
    // If Scala 3 is made the primary target, good linting scalac options for it should be added first.
    if3 = Seq(
      "-Ykind-projector:underscores",

      // disable new brace-less syntax:
      // https://alexn.org/blog/2022/10/24/scala-3-optional-braces/
      "-no-indent",

      // improve error messages:
      "-explain",
      "-explain-types",
    ),
  ),
  Compile / doc / scalacOptions ++= Seq("-groups", "-no-link-warnings"),
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  libraryDependencySchemes ++= Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % "always",
    "org.scala-lang.modules" %% "scala-xml" % "always",
  ),
  autoAPIMappings := true,
  versionScheme := Some("early-semver"),
  versionPolicyIntention := Compatibility.BinaryCompatible,
  // TODO: remove after 1.3.0 release
  versionPolicyIgnored ++= Seq(
    // ssl-config-core 0.6.1 and 0.7.1 ARE binary compatible, ignoring, just as Pekko does for 1.3.0 release
    // com.typesafe:ssl-config-core_2.13: incompatible version change from 0.6.1 to 0.7.1 (compatibility: package versioning policy)
    "com.typesafe" %% "ssl-config-core",
  ),
)

val alias =
  addCommandAlias("build", "+all compile test") ++
    addCommandAlias("fmt", "+all scalafmtAll scalafmtSbt") ++
    // `check` is called with `+` in release workflow
    addCommandAlias("check", "all versionPolicyCheck Compile/doc scalafmtCheckAll scalafmtSbtCheck")

val root = project
  .in(file("."))
  .settings(name := "pekko-extension")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(alias)
  .aggregate(
    serialization,
    pubsub,
    testActor,
    testHttp,
    distributedData,
    shardingStrategy,
    toolsTest,
    toolsUtil,
    toolsSerialization,
    toolsPersistence,
    toolsCluster,
    toolsInstrumentation,
    conhub,
    effectActor,
    effectTestkit,
    effectActorTests,
    effectPersistenceApi,
    effectPersistence,
    effectCluster,
    effectClusterSharding,
    effectEventsourcing,
  )

lazy val serialization = module("serialization")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      TestLib.ScalaTest % Test,
    ) ++ crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(Scodec.Scala2.Core % Optional),
      if3 = Seq(Scodec.Scala3.Core % Optional),
    ),
  )

lazy val pubsub = module("pubsub")
  .dependsOn(
    serialization,
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Stream,
      Pekko.ClusterTools,
      Pekko.Testkit % Test,
      Cats.Core,
      Cats.Effect,
      Evo.MetricTools,
      Evo.CatsHelper,
      Evo.SCache,
      Scodec.Bits,
      Evo.SMetrics,
      TestLib.ScalaTest % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(Scodec.Scala2.Core),
      if3 = Seq(Scodec.Scala3.Core),
    ),
  )

lazy val testActor = module("test-actor")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      TestLib.ScalaTest,
      // needed for testing Pekko modules version mismatch logic
      Pekko.OlderSlf4j % Test,
    ),
  )

lazy val testHttp = module("test-http")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Stream,
      PekkoHttp.Core,
      TestLib.ScalaTest,
      // needed for testing Pekko-Http modules version mismatch logic
      PekkoHttp.OlderTestkit % Test,
    ),
  )

lazy val distributedData = module("distributed-data")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Cluster,
      Pekko.DistributedData,
      Pekko.Testkit % Test,
      Cats.Core,
      Cats.Effect,
      Evo.ExecutorTools,
      Evo.CatsHelper,
      Evo.SMetrics,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val shardingStrategy = module("sharding-strategy")
  .dependsOn(
    distributedData,
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.DistributedData,
      Pekko.Cluster,
      Pekko.ClusterSharding,
      Pekko.Testkit % Test,
      Cats.Core,
      Cats.Effect,
      Evo.CatsHelper,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val toolsTest = module("tools-test")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Testkit,
      TestLib.ScalaTest,
    ),
  )

lazy val toolsUtil = module("tools-util")
  .dependsOn(
    toolsTest % "test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Misc.Logging,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val toolsSerialization = module("tools-serialization")
  .dependsOn(
    toolsTest % "test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Persistence,
      Misc.Logging,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val toolsPersistence = module("tools-persistence")
  .dependsOn(
    toolsSerialization,
    toolsTest % "test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Evo.ScalaTools,
      Evo.ConfigTools,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val toolsCluster = module("tools-cluster")
  .dependsOn(
    toolsTest % "test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Cluster,
      Pekko.ClusterSharding,
      Pekko.Testkit % Test,
      Misc.Logging,
      Evo.ConfigTools,
      Cats.Core,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val toolsInstrumentation = module("tools-instrumentation")
  .dependsOn(
    toolsUtil,
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Evo.ConfigTools,
      Prometheus.simpleclient,
    ),
  )

lazy val conhub = module("conhub")
  .dependsOn(
    serialization,
    toolsTest % "test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Scodec.Bits,
      Pekko.Actor,
      Pekko.Remote,
      Pekko.Cluster,
      Pekko.Stream,
      Pekko.Protobuf,
      Evo.ConfigTools,
      Evo.FutureHelper,
      Evo.SequentiallyPekko,
      Misc.Logging,
      Cats.Core,
      Cats.Effect % Test,
      Evo.SCache % Test,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(Scodec.Scala2.Core),
      if3 = Seq(Scodec.Scala3.Core),
    ),
  )

lazy val effectActor = module("effect-actor")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Cats.Core,
      Cats.Effect,
      Evo.CatsHelper,
      Pekko.Slf4j % Test,
      Pekko.Testkit % Test,
      Logback.Classic % Test,
      Logback.Core % Test,
      Slf4j.Api % Test,
      Slf4j.Log4jOverSlf4j % Test,
      TestLib.ScalaTest % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )

lazy val effectTestkit = module("effect-testkit")
  .dependsOn(
    effectActor,
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Cats.Effect,
      Evo.CatsHelper,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )

lazy val effectActorTests = module("effect-actor-tests")
  .dependsOn(
    effectActor % "test->test;compile->compile",
    effectTestkit % "test->test;test->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Testkit % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )

lazy val effectPersistenceApi = module("effect-persistence-api")
  .dependsOn(
    effectActor % "test->test;compile->compile",
    effectTestkit % "test->test;test->compile",
    effectActorTests % "test->test",
  )
  .settings(commonSettings)
  .settings(
    Compile / doc / scalacOptions -= "-Xfatal-warnings", // TODO get rid of this
  )
  .settings(
    libraryDependencies ++= Seq(
      Cats.Core,
      Cats.Effect,
      Evo.CatsHelper,
      Evo.SStream,
      Pekko.Slf4j % Test,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )

lazy val effectPersistence = module("effect-persistence")
  .dependsOn(
    effectPersistenceApi % "test->test;compile->compile",
    effectActor % "test->test;compile->compile",
    effectTestkit % "test->test;test->compile",
    effectActorTests % "test->test",
  )
  .settings(commonSettings)
  .settings(
    Compile / doc / scalacOptions -= "-Xfatal-warnings", // TODO get rid of this
  )
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Actor,
      Pekko.Stream,
      Pekko.Persistence,
      Pekko.PersistenceQuery,
      Cats.Core,
      Cats.Effect,
      Evo.CatsHelper,
      Evo.SMetrics,
      Pekko.PersistenceTestkit % Test,
      Pekko.Slf4j % Test,
      Pekko.Testkit % Test,
      TestLib.ScalaTest % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = List(Pureconfig.Pureconfig),
      if3 = List(Pureconfig.Scala3.Core, Pureconfig.Scala3.Generic),
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )

lazy val effectCluster = module("effect-cluster")
  .dependsOn(
    effectActor % "test->test;compile->compile",
    effectTestkit % "test->test;test->compile",
    effectActorTests % "test->test",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Cluster,
      Pekko.ClusterTyped % Test,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = List(Pureconfig.Pureconfig),
      if3 = List(Pureconfig.Scala3.Core, Pureconfig.Scala3.Generic),
    ),
  )

lazy val effectClusterSharding = module("effect-cluster-sharding")
  .dependsOn(
    effectCluster % "test->test;compile->compile",
    effectPersistence % "test->test;compile->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.ClusterSharding,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )

lazy val effectEventsourcing = module("effect-eventsourcing")
  .dependsOn(
    effectPersistence % "test->test;compile->compile",
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.Stream,
      Evo.Retry,
      Cats.Core,
      Cats.Effect,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Seq(compilerPlugin(Misc.KindProjector cross CrossVersion.full)),
      if3 = Nil,
    ),
  )
  .settings(
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Nil,
      if3 = List(Pureconfig.Scala3.Generic),
    ),
  )

def crossSettings[T](scalaVersion: String, if3: T, if2: T): T = {
  scalaVersion match {
    case version if version.startsWith("3") => if3
    case _ => if2
  }
}

def module(moduleName: String): Project =
  Project(id = moduleName, base = file(moduleName))
    .settings(name := s"pekko-extension-$moduleName")
