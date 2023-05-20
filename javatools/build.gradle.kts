import org.gradle.api.tasks.testing.logging.TestLogEvent

/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    id("org.xvm.java-library-conventions")
}

dependencies {
    // Make sure we can run tests without the jar task finished, and honor a standard build lifecycle
    implementation(project(":javatools_utils"))
}

tasks.jar {
    from(project(":lib_ecstasy").property("implicit.x"))
    from(project(":javatools_utils").sourceSets["main"].output)

    val version = libs.versions.xvm
    assert(rootProject.version.equals(version))

    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Main-Class"] = "org.xvm.tool.Launcher"
        attributes["Name"] = "/org/xvm/"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = version
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-prototype"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
}

tasks.build {
    finalizedBy(tasks["printSourceSets"])
}
