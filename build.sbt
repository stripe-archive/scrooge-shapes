organization in ThisBuild := "com.stripe"

val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

val circeVersion = "0.9.1"
val scalaCheckVersion = "1.13.5"
val scalaTestVersion = "3.0.5"
val scroogeVersion = "18.2.0"
val shapelessVersion = "2.3.3"
val thriftVersion = "0.9.2"

val baseSettings = Seq(
  scalacOptions ++= compilerOptions,
  coverageHighlighting := true,
  coverageScalacPluginVersion := "1.3.0",
  (scalastyleSources in Compile) ++= (unmanagedSourceDirectories in Compile).value
)

val allSettings = baseSettings ++ publishSettings

val docMappingsApiDir = settingKey[String]("Subdirectory in site target directory for API docs")

val root = project.in(file(".")).dependsOn(core).aggregate(core)

lazy val core = project
  .settings(allSettings)
  .settings(
    moduleName := "scrooge-shapes",
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided,
      "com.chuusai" %% "shapeless" % shapelessVersion,
      "org.apache.thrift" % "libthrift" % thriftVersion % Test,
      "com.twitter" %% "scrooge-core" % scroogeVersion % Test exclude("com.twitter", "libthrift"),
      "io.circe" %% "circe-generic-extras" % circeVersion % Test,
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test,
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ),
    ghpagesNoJekyll := true,
    docMappingsApiDir := "api",
    addMappingsToSiteDir(mappings in (Compile, packageDoc), docMappingsApiDir)
  )

lazy val publishSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/stripe/scrooge-shapes")),
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/stripe/scrooge-shapes"),
      "scm:git:git@github.com:stripe/scrooge-shapes.git"
    )
  ),
  developers := List(
    Developer(
      "travisbrown",
      "Travis Brown",
      "travisbrown@stripe.com",
      url("https://twitter.com/travisbrown")
    )
  )
)

credentials ++= (
  for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username,
    password
  )
).toSeq
