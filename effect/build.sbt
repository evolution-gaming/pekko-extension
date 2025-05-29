import Dependencies.*

lazy val commonSettings = Seq(
  organization         := "com.evolution",
  organizationName     := "Evolution",
  organizationHomepage := Some(url("http://evolution.com")),
  homepage             := Some(url("http://github.com/evolution-gaming/pekko-effect")),
  startYear            := Some(2019),
  scalaVersion         := crossScalaVersions.value.head,
  crossScalaVersions   := Seq("2.13.16", "3.3.6"),
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  scalacOptions ++= crossSettings(
    scalaVersion.value,
    if3 = List(
      "-release:17",
      "-Ykind-projector",
      "-language:implicitConversions",
      "-unchecked",
      "-feature",
      "-explain",
      "-explain-types",
      "-deprecation",
      "-Wunused:all",
    ),
    if2 = List("-release:17", "-Xsource:3", "-deprecation"),
  ),
  libraryDependencies ++= crossSettings(
    scalaVersion.value,
    if3 = Nil,
    if2 = Seq(compilerPlugin(`kind-projector` cross CrossVersion.full)),
  ),
  publishTo              := Some(Resolver.evolutionReleases),
  versionPolicyIntention := Compatibility.BinaryCompatible, // sbt-version-policy
  versionScheme          := Some("semver-spec"),
  licenses               := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
)

def crossSettings[T](scalaVersion: String, if3: T, if2: T): T =
  scalaVersion match {
    case version if version.startsWith("3") => if3
    case _                                  => if2
  }

val alias: Seq[sbt.Def.Setting[?]] =
  addCommandAlias("fmt", "scalafixEnable; scalafixAll; all scalafmtAll scalafmtSbt") ++
    addCommandAlias(
      "check",
      "all Compile/doc versionPolicyCheck scalafmtCheckAll scalafmtSbtCheck; scalafixEnable; scalafixAll --check",
    ) ++
    addCommandAlias("build", "all compile test")

lazy val root = project
  .in(file("."))
  .settings(name := "pekko-effect")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(alias)
  .aggregate(
    actor,
    `actor-tests`,
    testkit,
    persistence,
    `persistence-api`,
    eventsourcing,
    cluster,
    `cluster-sharding`,
  )

lazy val actor = project
  .in(file("actor"))
  .settings(name := "pekko-effect-actor")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.actor,
      Pekko.slf4j   % Test,
      Pekko.testkit % Test,
      Cats.core,
      CatsEffect.effect,
      Logback.classic          % Test,
      Logback.core             % Test,
      Slf4j.api                % Test,
      Slf4j.`log4j-over-slf4j` % Test,
      `cats-helper`,
      scalatest % Test,
    ),
  )

lazy val `actor-tests` = project
  .in(file("actor-tests"))
  .settings(name := "pekko-effect-actor-tests")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(actor % "test->test;compile->compile", testkit % "test->test;test->compile")
  .settings(
    libraryDependencies ++= Seq(
      Pekko.testkit % Test,
    ),
  )

lazy val testkit = project
  .in(file("testkit"))
  .settings(name := "pekko-effect-testkit")
  .settings(commonSettings)
  .dependsOn(actor)
  .settings(
    libraryDependencies ++= Seq(
      Pekko.testkit % Test,
      scalatest     % Test,
    ),
  )

lazy val `persistence-api` = project
  .in(file("persistence-api"))
  .settings(name := "pekko-effect-persistence-api")
  .settings(commonSettings)
  .dependsOn(
    actor         % "test->test;compile->compile",
    testkit       % "test->test;test->compile",
    `actor-tests` % "test->test",
  )
  .settings(
    libraryDependencies ++= Seq(
      Cats.core,
      CatsEffect.effect,
      `cats-helper`,
      sstream,
      Pekko.slf4j   % Test,
      Pekko.testkit % Test,
      scalatest     % Test,
    ),
  )

lazy val persistence = project
  .in(file("persistence"))
  .settings(name := "pekko-effect-persistence")
  .settings(commonSettings)
  .dependsOn(
    `persistence-api` % "test->test;compile->compile",
    actor             % "test->test;compile->compile",
    testkit           % "test->test;test->compile",
    `actor-tests`     % "test->test",
  )
  .settings(
    libraryDependencies ++= Seq(
      Pekko.actor,
      Pekko.stream,
      Pekko.persistence,
      Pekko.`persistence-testkit` % Test,
      Pekko.`persistence-query`,
      Pekko.slf4j   % Test,
      Pekko.testkit % Test,
      Cats.core,
      CatsEffect.effect,
      `cats-helper`,
      smetrics,
      scalatest % Test,
    ),
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = List(pureconfig),
      if3 = List(`pureconfig-scala3`, `pureconfig-generic-scala3`),
    ),
  )

lazy val eventsourcing = project
  .in(file("eventsourcing"))
  .settings(name := "pekko-effect-eventsourcing")
  .settings(commonSettings)
  .dependsOn(persistence % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      Pekko.stream,
      retry,
    ),
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = Nil,
      if3 = List(`pureconfig-generic-scala3`),
    ),
  )

lazy val cluster = project
  .in(file("cluster"))
  .settings(name := "pekko-effect-cluster")
  .settings(commonSettings)
  .dependsOn(actor % "test->test;compile->compile", testkit % "test->test;test->compile", `actor-tests` % "test->test")
  .settings(
    libraryDependencies ++= Seq(
      Pekko.cluster,
      Pekko.`cluster-typed` % Test,
    ),
    libraryDependencies ++= crossSettings(
      scalaVersion = scalaVersion.value,
      if2 = List(pureconfig),
      if3 = List(`pureconfig-scala3`, `pureconfig-generic-scala3`),
    ),
  )

lazy val `cluster-sharding` = project
  .in(file("cluster-sharding"))
  .settings(name := "pekko-effect-cluster-sharding")
  .settings(commonSettings)
  .dependsOn(
    cluster     % "test->test;compile->compile",
    persistence % "test->test;compile->compile",
  )
  .settings(
    libraryDependencies ++= Seq(
      Pekko.`cluster-sharding`,
    ),
  )
