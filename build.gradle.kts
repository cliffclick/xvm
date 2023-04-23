import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainUsageProgressDetails.JavaTool

/*
 * Main build file for the XVM project, producing the XDK.
 */
plugins {
    id("xvm.java-conventions")
}

println("from main build script: ${libs.versions.xvm.get()}")

group   = "org.xvm"
version = libs.versions.xvm.get()

subprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils"  )).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools"        )).using(project(":javatools"))
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

tasks.register("build") {
    group       = "Build"
    description = "Build all projects"

    dependsOn(project("xdk:").tasks["build"])
}
