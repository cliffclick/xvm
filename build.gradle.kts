import java.io.ByteArrayOutputStream

import org.jetbrains.gradle.ext.*

/*
 * Main build file for the XVM project, producing the XDK.
 */
plugins {
    base
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("com.dorongold.task-tree") version "2.1.1" // enables the 'gradle <task_1> [task_2 ... task_n] taskTree' task
    id("org.xvm.project.conventions")
}

val getXvmVersion: () -> String by extra
println("getXvmVersion: " + getXvmVersion())

group = "org.xvm"
version = getXvmVersion()

// TODO REMOVE THESE
if ("true".equals(System.getenv("DEBUG_BUILD"), ignoreCase = true)) {
    gradle.startParameter.showStacktrace = org.gradle.api.logging.configuration.ShowStacktrace.ALWAYS_FULL
    gradle.startParameter.logLevel = org.gradle.api.logging.LogLevel.DEBUG
    println("Warning: DEBUG_BUILD is enabled, and output may be quite verbose.")
}

subprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils")).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools")).using(project(":javatools"))
        }
    }
}

// Base tasks: clean, check, assemble, build, buildCONFIGURATION, cleanTASK
// The build task will be the default implementation, and since the settings.gradle.kts already
// include all subprojects, we won't need a specific build task declared here. This is exactly
// What it would do already
/*
tasks.register("build") {
    group = "Build"
    description = "Build all projects"
    dependsOn(project("xdk:").tasks["build"])
}*/

val gitCleanTask = tasks.register("cleanAll") {
    group = "Delete"
    description = "Cleans everything, including the Gradle cache, and items not under source control. Also runs 'gradle clean'"
    exec {
        standardOutput = ByteArrayOutputStream()
        workingDir = rootDir
        executable = "git"
        args("clean", "-nxfd", "-e", ".idea")
        doLast {
            println("gitClean:")
            standardOutput.toString().lines().forEach {
                println("  $it")
            }
        }
    }
    finalizedBy(tasks["clean"]) // should run Gradle clean AFTER gitClean, to ensure Gradle clean has no caches left.
}

/**
 * Task that can be used to do a full rebuild of the entire project. Gradle clean
 * keeps cached state and other things by design, but it is useful to be able to
 * do a full rebuild, especially while we don't have full Gradle lifecycle support
 * and IDE integration for XTC
 */
tasks.register("rebuild") {

}

/*
 * Provides a hook where projects have been evaluated before the build.
 *
 * Currently not used, but will likely come into play if we need to represent dependencies
 * that cannot be calculated by just hooking into the Gradle build lifecycle, as is best practice.
 */

val manualTestsDir = project(":manualTests").projectDir.absolutePath

apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")

idea {
    project {
        // This is the root project: "xvm"
        assert("xvm".equals(name))

        vcs = "git"
        settings {
            /*
             * Make sure IntelliJ generates iml files for modules. We need to modify the dependencies
             * of the project settings -> modules -> javatools -> dependencies so that we add the
             * lib_ecstasy dependencies that are copied there in a non-standard way for Gradle, so that
             * IntelliJ doesn't automatically detect the copied resource path.
             *
             * A callback can be used to post process an IDEA project file after the sync process has finished.
             To achieve this, if any callbacks are registered using APIs described below, a special task
             * processIdeaSettings will be called after import. It will perform the callbacks providing
             * file paths or content to be updated. For iml files to exist and be modifiable,
             * Generate *.iml files... checkbox must be set to true in Gradle settings
             * (File | Settings | Build, Execution, Deployment | Build Tools | Gradle)
             */
            generateImlFiles = true

            // https://youtrack.jetbrains.com/issue/IDEA-286095/Gradle-IDEA-Ext-Unclear-how-to-generate-iml-files-for-project-not-yet-imported
            delegateActions {
                // Change to false for automatic IDEA integration, but there are issues automatically configuring our
                // non-standard configuration for project module info and resource directories. This should normally
                // be automatically picked up for a known language project, as long as it conforms to the declarative
                // behavior of the Gradle standard lifecycle, which XTC currently doesn't do completely.
                //
                // It is a complex undertaking to support IntelliJ integration for the sample run configurations
                // that works out of the box, as long as portions of are build are implemented with significant
                // amounts of custom code, rather than sticking as close as possible to the declarative standard
                // preferred by Gradle. In short: to play well with Gradle, any build logic should be as declarative
                // as possible, meaning that you tell Gradle what you want done, but do not tell it how and/or in
                // which order to do it, and striving to add as little explicit build logic / "code" as possible.
                // An XTC language would be the best way to get there, both for the pure Gradle build, but also
                // for any IDE integrations with e.g. debuggers, lexing and breakpoints.
                delegateBuildRunToGradle = true
                testRunner = ActionDelegationConfig.TestRunner.GRADLE // PLATFORM
                println("Delegate build runs to Gradle: $delegateBuildRunToGradle (testRunner: $testRunner)")
            }

            compiler {
                enableAutomake = false
                autoShowFirstErrorInEditor = true
                parallelCompilation = true
                javac {
                    javacAdditionalOptions = "-encoding UTF-8" // TODO: Add a pedantic mode with e.g. -Xlint:all too
                    generateDeprecationWarnings = true
                }
                encodings {
                    encoding = "UTF-8"
                }
            }
        }
    }
}

/*
gradle.taskGraph.whenReady {
    println("PROJECT TASK GRAPH IS READY:")
    allTasks.forEach {
        println("   task: ${it.project.name}#${it.name}")
    }
    idea.project.settings.taskTriggers {
        beforeSync(tasks.register("beforeSync") {
            doLast {
                println("BeforeSync")
            }
        })
        /*beforeBuild
        beforeRebuild
        beforeSync
        beforeEvaluate
        afterBuild
        afterRebuild
        afterSync
        afterEvaluate*/
        //afterSync("list of tasks")
    }
}
*/
