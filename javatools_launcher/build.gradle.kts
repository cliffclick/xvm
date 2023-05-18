/*
 * Build file for the javatools_launcher project.
 *
 * The launcher programs need to be built on different hardware/OS combinations, so this project is
 * not currently automated.
 *
 * TODO: We can handle this with the Gradle native build extensions, if we want to integrate
 *   it into the rest of the project build, and avoid the explicit makefile context.
 */

plugins {
    id("cpp-application")
}

// FOR NOW - DO NOT clean the build directory, those are checked in binaries
// unless we complete this native compilation plugin.
tasks.named<Task>("clean") {
    enabled = false
    doLast {
        println("Disabled ${project.name}.clean task until Gradle integration of launcher builds.")
    }
}

application {
    targetMachines.set(listOf(
        machines.linux.x86_64,
        machines.windows.x86_64,
        machines.macOS.x86_64))
        //machines.macOS.arch64))
}
