import com.typesafe.tools.mima.core._
import Dependencies._
val scala211 = "2.11.12"
val scala212 = "2.12.18"
val scala213 = "2.13.11"
val scala3 = "3.3.0"

addCommandAlias("fmt", "; scalafmtAll; scalafmtSbt")
addCommandAlias("fmtCheck", "; scalafmtCheckAll; scalafmtSbtCheck")

tlReplaceCommandAlias("prePR", "; githubWorkflowGenerate ; +fmt; bench/compile; +test")

ThisBuild / tlBaseVersion := "0.3"
ThisBuild / startYear := Some(2021)
ThisBuild / developers += tlGitHubDev("johnynek", "P. Oscar Boykin")

ThisBuild / crossScalaVersions := List(scala211, scala212, scala213, scala3)
ThisBuild / scalaVersion := scala213

ThisBuild / tlVersionIntroduced := Map("3" -> "0.3.4")

ThisBuild / tlCiDependencyGraphJob := false // omit after dropping scala 2.11
ThisBuild / githubWorkflowBuildMatrixExclusions ++=
  Seq(
    MatrixExclude(Map("project" -> "rootJS", "scala" -> "2.11")),
    MatrixExclude(Map("project" -> "rootNative", "scala" -> "2.11"))
  )

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    id = "coverage",
    name = "Generate coverage report",
    scalas = Nil,
    sbtStepPreamble = Nil,
    steps = List(WorkflowStep.Checkout) ++ WorkflowStep.SetupJava(
      githubWorkflowJavaVersions.value.toList
    ) ++ githubWorkflowGeneratedCacheSteps.value ++ List(
      WorkflowStep.Sbt(List("coverage", "rootJVM/test", "coverageAggregate")),
      WorkflowStep.Use(
        UseRef.Public(
          "codecov",
          "codecov-action",
          "v3"
        )
      )
    )
  )
)

ThisBuild / licenses := List(License.MIT)

lazy val root = tlCrossRootProject.aggregate(core, bench)

lazy val docs =
  project.in(file("site")).enablePlugins(TypelevelSitePlugin).dependsOn(core.jvm, bench)

lazy val isScala211 = Def.setting {
  scalaBinaryVersion.value == "2.11"
}

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(
    name := "cats-parse",
    libraryDependencies ++= {
      Seq(
        if (isScala211.value) cats211.value else cats.value,
        munit.value % Test,
        munitScalacheck.value % Test
      )
    },
    libraryDependencies ++= {
      if (tlIsScala3.value) Nil else Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
    },
    scalacOptions ++= {
      // this code seems to trigger a bug in 2.11 pattern analysis
      if (isScala211.value) List("-Xno-patmat-analysis") else Nil
    },
    tlFatalWarnings := {
      if (isScala211.value) false
      else tlFatalWarnings.value
    },
    mimaPreviousArtifacts := {
      if (isScala211.value) Set.empty else mimaPreviousArtifacts.value
    },
    mimaBinaryIssueFilters ++= {
      /*
       * It is okay to filter anything in Impl or RadixNode which are private
       */
      if (tlIsScala3.value)
        List(
          ProblemFilters.exclude[DirectMissingMethodProblem]("cats.parse.Parser#Error.fromProduct"),
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("cats.parse.Parser#Error.unapply"),
          ProblemFilters.exclude[MissingTypesProblem]("cats.parse.Parser$Error$"),
          ProblemFilters.exclude[IncompatibleResultTypeProblem]("cats.parse.Parser#Error.unapply"),
          ProblemFilters.exclude[DirectMissingMethodProblem]("cats.parse.Parser#Error.fromProduct")
        )
      else Nil
    } ++ MimaExclusionRules.parserImpl ++ MimaExclusionRules.bitSetUtil
  )
  .jvmSettings(
    // We test against jawn on JVM for some json parsers
    libraryDependencies +=
      (if (isScala211.value) jawnAst211.value else jawnAst.value) % Test
  )
  .jsSettings(
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filterNot(_.startsWith("2.11")),
    coverageEnabled := false
  )
  .nativeSettings(
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filterNot(_.startsWith("2.11")),
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.3.8").toMap,
    coverageEnabled := false
  )

lazy val bench = project
  .enablePlugins(JmhPlugin, NoPublishPlugin)
  .settings(
    name := "bench",
    coverageEnabled := false,
    Compile / unmanagedSources := {
      if (Set("2.12", "2.13").contains(scalaBinaryVersion.value)) {
        (Compile / unmanagedSources).value
      } else Nil
    },
    libraryDependencies ++= {
      if (Set("2.12", "2.13").contains(scalaBinaryVersion.value))
        Seq(
          fastParse,
          parsley,
          jawnAst.value,
          parboiled,
          attoCore
        )
      else Nil
    }
  )
  .dependsOn(core.jvm)
