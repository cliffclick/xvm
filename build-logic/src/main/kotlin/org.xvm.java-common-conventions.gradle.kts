/**
 * Conventions common for all projects that use Java, and their tests
 */
plugins {
    id("org.xvm.project.conventions")
    java
}

val versionCatalog : VersionCatalog by extra

// TODO: This should automatically come from settings.gradle.kts already, remove it?
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

/**
 * Default Java toolchain version to use if version catalog does not specify which Java we need
 * (all versions of dependencies should be in rootDir/gradle/libs.versions.toml)
 */
java {
    toolchain {
        val jdkVersion : String by extra
        println("Java Toolchain will use jdkVersion: $jdkVersion")
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
    }
}

tasks.named<Test>("test") {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
