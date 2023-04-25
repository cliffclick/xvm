import org.jetbrains.gradle.ext.*
import org.gradle.api.XmlProvider
import org.gradle.internal.impldep.org.apache.commons.io.output.ByteArrayOutputStream

/*
 * Main build file for the XVM project, producing the XDK.
 */
plugins {
    base
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("com.dorongold.task-tree") version "2.1.1" // enables the 'gradle <task_1> [task_2 ... task_n] taskTree' task
}

group = "org.xvm"
version = libs.versions.xvm.get()

gradle.startParameter.showStacktrace = org.gradle.api.logging.configuration.ShowStacktrace.ALWAYS_FULL
gradle.startParameter.logLevel = org.gradle.api.logging.LogLevel.DEBUG

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
        standardOutput = java.io.ByteArrayOutputStream()
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
                delegateBuildRunToGradle = false
                testRunner = ActionDelegationConfig.TestRunner.PLATFORM
                println("Delegate build runs to gradle: $delegateBuildRunToGradle (testRunner: $testRunner)")
            }

            compiler {
                enableAutomake = false
                autoShowFirstErrorInEditor = true
                parallelCompilation = true
                javac {
                    javacAdditionalOptions = "-encoding UTF-8 -deprecation" // TODO: -Xlint:all in pedantic mode
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
