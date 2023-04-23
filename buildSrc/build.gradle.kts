/**
 * buildSrc/build.gradle.kts
 *
 * Shared build logic, and project type specific convention plugins (e.g. java projects).
 * Version catalogue declared in settings.gradle.kts. Should never explicitly refer to any
 * version of plugin id, in any way not going through the versionCatalog access.
 */

// Sharing build logic example: https://blog.jdriven.com/2021/02/gradle-goodness-shared-configuration-with-conventions-plugin/

plugins {
    `kotlin-dsl`
    `version-catalog`
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal() // enable external plugins to be resolved in the dependencies for buildSrc (defined below)
}

// Horrible bug in version catalogs: https://github.com/gradle/gradle/issues/15383
val catalogs = extensions.getByType<VersionCatalogsExtension>()
//println("Catalogs: $catalogs")
val xvmVersion = catalogs.named("libs").findVersion("xvm").get()
val jdkVersion = catalogs.named("libs").findVersion("jdk").get()

//val xvmVersion = libs.versions.xvm.get()
// val jdkVersion = libs.versions.jdk.get()

print("Version catalogue versions in buildScriptRoot: xvm=$xvmVersion, jdk=$jdkVersion")

// Remaining before language plugin cherry picking to this branch
//    Version catalgue support and workaround
//    Take away implicit copy tasks etc, and use resurce sets and source sets instead of copies.
//    Remove mustRunBefore constraints for many things
//    The "abstract" projects that glom subprojects together probably have a better best-practice.

// Figure out how to get any sample language plugin work happily with the current code versions.
// Does not compile on modern Gradle versions at the moment:
//   https://github.com/JetBrains/intellij-sdk-code-samples/tree/main/simple_language_plugin

// IDEA Sync - delay sync until IDEA configuration is read. Is finalzied by, enough, and the
// Gradle API object? Possibly. Better than the task triggers, I hope.
// Also - sort out the withXml format. This is strange.

// Needed tools:
//   PLugin verifier (built-in/marketplace)https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html#ide-support
// Code samples:
//   https://github.com/JetBrains/intellij-sdk-code-samples
// Plugins: Grammar-Kit, PsiViewer

// Thread that complains a lot about no support, and no new updates, and incompatibilities with modern gradle etc...
// So even the build in langauge support appears to be the standard IDEA abandonware :-(

