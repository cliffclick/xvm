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

fun versionCatalogLookupVersion(name : String, defaultValue : Any? = null): String {
    val version = versionCatalogLibs().findVersion(name)
    if (version.isPresent) { // TODO: There has to be a pretty kotlin construct for this Java style Optional
        return version.get().toString()
    }
    if (defaultValue == null) {
        throw NoSuchElementException(name)
    }
    return defaultValue.toString()
}

val junit = versionCatalogLookupLibrary("junit")

dependencies {
    testImplementation(junit)
}

// TODO: Ugly hack, while plugin version catalog resolution for precompiled scripts is broken in Gradle
val defaultJdkVersion = 17
val jdkVersion = versionCatalogLookupVersion("jdk", defaultJdkVersion)

java {
    toolchain {
        val ver : String = jdkVersion
        println("Java Toolchain will use jdkVersion: $ver")
        languageVersion.set(JavaLanguageVersion.of(ver))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
