/**
 * The build-logic project contains common build logic, and supersedes buildSrc in earlier
 * Gradle standards. This is where we store conventions for the different types of project,
 * and common logic for building them.
 */

plugins {
    `kotlin-dsl` // Enable precompiled scripts for build-logic project, which supersedes buildSrc
    alias(libs.plugins.kotlin.jvm) // Support convention plugins written in Kotlin. They are scripts in 'src/main/kotlin' that automatically become available as plugins in the main build.
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
}
