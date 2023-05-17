/**
 * Conventions common for all projects that use Java, and their tests
 */
plugins {
    id("org.xvm.project-conventions")
    java
}

val versionCatalog : VersionCatalog by extra

dependencies {
    constraints {
        versionCatalog.findLibrary("apache-commons-text").ifPresent() {
            implementation(it)
        }
    }

    versionCatalog.findLibrary("junit").ifPresent {
        println("${javaProjectName()} testImplementation: JUnit (version: ${it.get()})")
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
        println("${javaProjectName()} toolchain: JDK version $jdkVersion")
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
    }
}

tasks.named<Test>("test") {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
    println("${javaProjectName()} task: $name.maxHeapSize = $maxHeapSize, uses JUnit.")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    println("${javaProjectName()} task: $name.options.encoding = ${options.encoding}")
}

fun javaProjectName() : String {
    return "'${project.name}' (Java project):"
}
