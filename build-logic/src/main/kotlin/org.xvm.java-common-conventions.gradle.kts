/**
 * Conventions common for all projects that use Java, and their tests
 */
plugins {
    java
}

/**
 * Default Java toolchain version to use if version catalog does not specify which Java we need
 * (all versions of dependencies should be in rootDir/gradle/libs.versions.toml)
 */

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val defaultJdkVersion = 17
val jdkVersion = resolveJdkVersion()

println("jdkVersion: $jdkVersion")

val getJdkVersion by extra {
    fun() : String {
        return resolveJdkVersion()
    }
}

repositories {
    mavenCentral()
}

dependencies {
    constraints {
        // Define dependency versions as constraints
        implementation("org.apache.commons:commons-text:1.10.0")
    }
    versionCatalog.findLibrary("junit").ifPresent {
        println("conventions:common-java:junit: $it")
        testImplementation(it)
    }
}

java {
    toolchain {
        val ver : String = resolveJdkVersion()
        println("Java Toolchain will use jdkVersion: $ver")
        languageVersion.set(JavaLanguageVersion.of(ver))
    }
}

tasks.named<Test>("test") {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

fun versionCatalogLookupLibrary(name : String): Provider<MinimalExternalModuleDependency> {
    return versionCatalog.findLibrary(name).get()
}

fun versionCatalogLookupVersion(name : String, defaultValue : Any? = null): String {
    val version = versionCatalog.findVersion(name)
    if (version.isPresent) { // TODO: There has to be a pretty Kotlin construct for this Java style Optional
        return version.get().toString()
    }
    if (defaultValue == null) {
        throw NoSuchElementException(name)
    }
    return defaultValue.toString()
}

fun resolveJdkVersion() : String {
    return versionCatalogLookupVersion("jdk", defaultJdkVersion)
}

