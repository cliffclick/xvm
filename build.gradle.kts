import org.jetbrains.gradle.ext.*

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    //id("java") apply true
    id("idea")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7" apply true
}

group = "org.xvm"
version = "0.4.3"

defaultTasks("clean", "build")

idea {
    module {
        inheritOutputDirs = true
        println(pathVariables)
    }
    project {
        settings {
            compiler {
                processHeapSize = 1024 // Gradle daemon max heap size, default is 700
                autoShowFirstErrorInEditor = true
                parallelCompilation = true
                rebuildModuleOnDependencyChange = true
                javac {
                    javacAdditionalOptions = "-encoding UTF-8 -deprecation -Xlint:all --enable-preview "
                }
            }
            /*            runConfigurations {
                            // TODO: Run build task java_tools before, or at least be a dependency
                            "HelloWorld"(org.jetbrains.gradle.ext.Application) {

                            }
                       }*/
            //settings.generateImlFiles
            delegateActions {
                // Always delegate "Build and Run" and "Test" actions from Gradle to IntelliJ.
                delegateBuildRunToGradle = false
                testRunner = ActionDelegationConfig.TestRunner.PLATFORM
            }
            encodings {
                encoding = "UTF-8"
            }
        }
    }
    workspace {

    }
}

allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils")).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools")).using(project(":javatools"))
        }
    }

    repositories {
        mavenCentral {
            content {
                excludeGroup("org.xtclang.xvm")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}


println(allprojects)
println(subprojects)
println(gradle.includedBuilds)
println("****")
//println(project("xdk:").tasks["build"])
println("****")

//tasks.forEach(println("Task " + t))
//tasks["build"].dependsOn(project("xdk:").tasks["build"])

tasks.register("build") {
    group = "Build"
    description = "Build all projects"
    //doLast {
        val deps = project("xdk:").tasks["build"]
        println("deps: " + deps)
        dependsOn(deps)
    //}
}

project("xdk:")

task("gitClean") {
    group = "other"
    description = "Runs git clean, recursively from the repo root. Default is dry run."

    doLast {
        exec {
            val dryRun = !"false".equals((project.findProperty("gitCleanDryRun") ?: "true").toString(), ignoreCase = true)
            logger.lifecycle("Running gitClean task...")
            if (dryRun) {
                logger.warn("WARNING: gitClean is in dry run mode. To explicitly run gitClean, use '-PgitCleanDryRun=false'.")
            }
            commandLine("git", "clean", if (dryRun) "-nfxd" else "-fxd", "-e", ".idea")
        }
    }
}
