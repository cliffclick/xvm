import gradle.kotlin.dsl.accessors._8a0c834e2c3bd9345c663be0c81ffd6c.javaToolchains

/**
 * Conventions common for all projects that use Java, and their tests
 */
plugins {
    java
    id("org.xvm.project-conventions")
}

val findLibrary: (String) -> Provider<MinimalExternalModuleDependency> by extra

dependencies {
    val junit = findLibrary("junit")
    testImplementation(junit)
    println("${javaProjectName()} testImplementation: JUnit (${junit.get()})")

    constraints {
        val commonsText = findLibrary("apache-commons-text")
        implementation(commonsText)
        println("${javaProjectName()} apache-commons-text: (${commonsText.get()})")
    }
}

/**
 * Default Java toolchain version to use if version catalog does not specify which Java we need
 * (all versions of dependencies should be in rootDir/gradle/libs.versions.toml)
 */
java {
    toolchain {
        val jdkVersion : String by extra
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
        println("${javaProjectName()} Java toolchain language version: $jdkVersion")
    }
}

tasks.named<Test>("test") {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
    println("${javaProjectName()} task: $name.maxHeapSize = $maxHeapSize, uses JUnit.")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-deprecation")
    println("${javaProjectName()} task: $name.options [encoding: ${options.encoding}, compilerArgs: ${options.compilerArgs}]")
}

fun javaProjectName() : String {
    return "'${project.name}' (Java project):"
}
