plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    constraints {
        // Define dependency versions as constraints
        implementation("org.apache.commons:commons-text:1.10.0")
    }
}

tasks.named<Test>("test") {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
}

fun Project.versionCatalogLibs(): VersionCatalog {
    return extensions.getByType<VersionCatalogsExtension>().named("libs")
}

fun versionCatalogLookupLibrary(name : String): Provider<MinimalExternalModuleDependency> {
    return versionCatalogLibs().findLibrary(name).get()
}

fun versionCatalogLookupVersion(name : String): VersionConstraint {
    return versionCatalogLibs().findVersion(name).get()
}

val junit = versionCatalogLookupLibrary("junit")
println("JUnit version catalog entry: $junit")

dependencies {
    testImplementation(junit)
}

// TODO: Ugly hack, while plugin version catalog resolution for precompiled scripts is broken in Gradle
val jdkVersion = versionCatalogLookupVersion("jdk")
val defaultJdkVersion = 17

java {
    toolchain {
        val ver = jdkVersion as? Int ?: defaultJdkVersion
        println("jdkVersion: $ver")
        languageVersion.set(JavaLanguageVersion.of(ver))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    //options.compilerArgs = listOf("-Xlint:unchecked")
}

fun resolveIsCI() : String {
    return System.getenv("CI")
}

fun resolveBuildNum() : String {
    return System.getenv("BUILD_NUMBER")
}
