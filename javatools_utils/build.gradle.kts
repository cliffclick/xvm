import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    id("org.xvm.java-library-conventions")
}

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

tasks.build {
    finalizedBy(tasks["printSourceSets"]) // TODO Why aren't outputs finalized?
}
