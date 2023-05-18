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

application {
    targetMachines.set(listOf(
        machines.linux.x86_64,
        machines.windows.x86_64,
        machines.macOS.x86_64))
        //machines.macOS.arch64))
}
