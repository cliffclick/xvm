import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.cli.jvm.compiler.writeOutput
import org.jetbrains.kotlin.gradle.utils.`is`

/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    id("org.xvm.java-library-conventions")
}

fun printSourceSets() {
    sourceSets.all {
        var empty = true
        println(this)

        println("  sources:")
        allSource.forEach {
            println("    source file: ${it.absolutePath}")
            empty = false
        }
        if (empty) println("    empty")

        empty = true
        println("  outputs: " + output.isEmpty)
        output.asFileTree.files.forEach {
            println("    output file: ${it.absolutePath}")
            empty = false
        }
        if (empty) println("    empty")
    }
}

printSourceSets()

tasks.build {
    doFirst {
        println("build ${project.name}")
    }
}
/*
tasks.test {
    doLast {
        println("Finished running tests.")
    }
}*/

tasks.jar {
    val version = libs.versions.xvm
    assert(rootProject.version.equals(version))

    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Name"] = "/org/xvm/util"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = version
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-javatools_utils"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "xtclang.org"
    }

    println(project.layout)
}
