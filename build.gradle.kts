import java.io.ByteArrayOutputStream

import org.jetbrains.gradle.ext.*

/*
 * Main build file for the XVM project, producing the XDK.
 *
 * TODO: Right now the XDK script is as bit of a "God class" anti-pattern, and also runs in a very non-parallel
 *   manner, with no support for incremental builds or automatic caching. We want to spread this task out into
 *   every subproject, instead of having a master script for just the XDK, which knows about its sub projects.
 *   This will massively facilitate creating an XTC native Gradle build life cycle, and a language integration,
 *   both with Gradle in general, as well as language integration into IDEs for debugging, highlighting,
 *   auto-completion, etc. Trying to do the integration first, is blocked by a large amount of assumptions
 *   from IDE vendors and Gradle standard practices, that were not intended to support the current kind of
 *   build model.
 */

plugins {
    id("org.xvm.project-conventions")
    alias(libs.plugins.idea.ext)
    alias(libs.plugins.task.tree) // enables the 'gradle <task_1> [task_2 ... task_n] taskTree' task
}

val xvmVersion: String by extra

group = "org.xvm"
version = xvmVersion

// TODO REMOVE THESE
if ("true".equals(System.getenv("DEBUG_BUILD"), ignoreCase = true)) {
    gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL
    gradle.startParameter.logLevel = LogLevel.DEBUG
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

    // Enable listing dependencies for all projects, with "./gradlew allDeps"
    tasks.register<DependencyReportTask>("allDeps")
}

val buildTask = tasks.register("build") {
    group = "Build"
    description = "Build all projects"
    dependsOn(project("xdk:").tasks["build"])
    doFirst {
        println("Running root project build for '${project.name}'...")
    }
    doLast {
        println("Finished root project build for '${project.name}'.")
    }
}

val cleanTask = tasks.register("clean") {
    group = "Delete"
    description = "Call clean tasks in all subprojects, if they exist."

    val projectName = project.name
    subprojects.forEach {
        val subprojectName = it.name
        val cleanSubprojectTask = it.tasks.findByName("clean")
        if (cleanSubprojectTask != null) {
            println("'$projectName.clean' task: adding dependency to '$subprojectName.clean'")
            dependsOn(cleanSubprojectTask)
        } else {
            println("     (project '$subprojectName' has no 'clean' task; none will be added)")
        }
    }
}

/**
 * Clean out all files not checked into git, including Gradle caches under the root directory.
 * The .idea directory is populated with some default configurations, but likely not to the detail
 * we want them, by using the idea-ext plugin, so currently that is deleted as well. This is a good
 * thing, because there are bugs in the IntelliJ Kotlin DSL at times, that require settings to be
 * wiped, but it may not be great if the developer has significant configuration changes to IntelliJ,
 * so we may want to add a mode where we preserve the .idea directory in the root.
 */
val gitCleanTask = tasks.register("gitClean") {
    group = "Delete"
    description = "Cleans everything, including the Gradle cache, not under source control."
    exec {
        standardOutput = ByteArrayOutputStream()
        workingDir = rootDir
        executable = "git"
        args("clean", "-xfd")
        //args("clean", "-xfd", "-e", ".idea") // TODO:
        doLast {
            println("gitClean:")
            standardOutput.toString().lines().forEach {
                println("  $it")
            }
        }
    }
}

/**
 * Task that can be used to do a full rebuild of the entire project. Gradle clean
 * keeps cached state and other things by design, but it is useful to be able to
 * do a full rebuild, especially while we don't have full Gradle lifecycle support
 * and IDE integration for XTC
 */
var cleanAllTask = tasks.register("cleanAll") {
    group = "Delete"
    description = "Cleans everything, including any Gradle and IDEA state, not under source control under the root."
    dependsOn(gitCleanTask, cleanTask)
}

tasks.register("rebuild") {
    group = "Build"
    description = "Erase all current state of the build, and redo the build from scratch"
    dependsOn(cleanAllTask)
    finalizedBy(buildTask)
    doFirst {
        println("Rebuilding all, after deleting *all* cached data under the repo root...")
    }
}

/**
 * The IDEA plugin tasks have been deprecated. It should not be called, as it creates legacy project files
 * Somewhat suboptimally, the only way the "idea" configuration is applied nowadays, is completely out of
 * the hands of our build system, called behind the scenes by the IntelliJ tooling API, and that is not
 * ideal.
 */

/* TODO: Temporarily commented out to see if this is what breaks the IDEA settings
afterEvaluate {
    println("Root project '$name' has finished evaluation (and by inference, all its subprojects).")
    listOfNotNull("ideaModule", "ideaProject", "ideaWorkspace", "idea").forEach {
        val task = tasks.findByName(it)
        if (task != null) {
            println("Disabling deprecated task '$it'")
            task.enabled = false
            task.doFirst {
                throw IllegalArgumentException("'$it' task has been disabled. It is deprecated and should not be used.")
            }
        }
    }
}*/

/*
 * Provides a hook where projects have been evaluated before the build.
 *
 * Currently not used, but will likely come into play if we need to represent dependencies
 * that cannot be calculated by just hooking into the Gradle build lifecycle, as is best practice.
 */

val manualTestsDir = project(":manualTests").projectDir.absolutePath

idea {
    project {
        // This is the root project: "xvm"
        assert("xvm".equals(name))

        vcs = "git"
        settings {
            /**
             * There is not enough support in either the deprecated "idea" plugin or in "idea-ext" to actually
             * manipulate contents of the config files. This is best done manually, if we need to, but not as
             * part of the existing "idea-ext" plugin logic. This is a later project.
             */
            generateImlFiles = false

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
                testRunner = ActionDelegationConfig.TestRunner.GRADLE // "PLATFORM" for IDEA.
                println("Delegate build runs to Gradle: $delegateBuildRunToGradle (testRunner: $testRunner)")
            }

            compiler {
                autoShowFirstErrorInEditor = true
                enableAutomake = false
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
Basic runnable tasks without even the base plugin (--all)

Build Setup tasks
-----------------
init - Initializes a new Gradle build.
wrapper - Generates Gradle wrapper files.

Help tasks
----------
buildEnvironment - Displays all buildscript dependencies declared in root project 'test'.
dependencies - Displays all dependencies declared in root project 'test'.
dependencyInsight - Displays the insight into a specific dependency in root project 'test'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
kotlinDslAccessorsReport - Prints the Kotlin code for accessing the currently available project extensions and conventions.
outgoingVariants - Displays the outgoing variants of root project 'test'.
projects - Displays the sub-projects of root project 'test'.
properties - Displays the properties of root project 'test'.
resolvableConfigurations - Displays the configurations that can be resolved in root project 'test'.
tasks - Displays the tasks runnable from root project 'test'.

Other tasks
-----------
components - Displays the components produced by root project 'test'. [deprecated]
dependentComponents - Displays the dependent components of components in root project 'test'. [deprecated]
model - Displays the configuration model of root project 'test'. [deprecated]
prepareKotlinBuildScriptModel

Adding the BASE plugin gives us a life cycle skeleton for the build as:

> Task :tasks

------------------------------------------------------------
Tasks runnable from root project 'test'
------------------------------------------------------------

Build tasks
-----------
assemble - Assembles the outputs of this project.
build - Assembles and tests this project.
clean - Deletes the build directory.

Build Setup tasks
-----------------
init - Initializes a new Gradle build.
wrapper - Generates Gradle wrapper files.

Help tasks
----------
buildEnvironment - Displays all buildscript dependencies declared in root project 'test'.
dependencies - Displays all dependencies declared in root project 'test'.
dependencyInsight - Displays the insight into a specific dependency in root project 'test'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
kotlinDslAccessorsReport - Prints the Kotlin code for accessing the currently available project extensions and conventions.
outgoingVariants - Displays the outgoing variants of root project 'test'.
projects - Displays the sub-projects of root project 'test'.
properties - Displays the properties of root project 'test'.
resolvableConfigurations - Displays the configurations that can be resolved in root project 'test'.
tasks - Displays the tasks runnable from root project 'test'.

Verification tasks
------------------
check - Runs all checks.

Other tasks
-----------
components - Displays the components produced by root project 'test'. [deprecated]
dependentComponents - Displays the dependent components of components in root project 'test'. [deprecated]
model - Displays the configuration model of root project 'test'. [deprecated]
prepareKotlinBuildScriptModel

Rules
-----
Pattern: clean<TaskName>: Cleans the output files of a task.
Pattern: build<ConfigurationName>: Assembles the artifacts of a configuration.

BUILD SUCCESSFUL in 313ms

Note that clean always exists.

*/