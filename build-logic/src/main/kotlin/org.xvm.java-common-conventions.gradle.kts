import org.gradle.api.tasks.testing.logging.TestLogEvent

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

tasks.withType<Test> {
    useJUnit() // TODO: Migrate to useJUnitPlatform(), Jupiter.
    maxHeapSize = "1G"
    println("${javaProjectName()} task: $name.maxHeapSize = $maxHeapSize, uses JUnit.")
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStackTraces = false // change to true to see test output in build log
    }
}

tasks.withType<JavaCompile>() { // .configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-deprecation")
    println("${javaProjectName()} task: $name.options [encoding: ${options.encoding}, compilerArgs: ${options.compilerArgs}]")
}

tasks.register("printSourceSets") {
    doLast {
        println("${project.name} source sets:")
        sourceSets.all {
            var empty = true
            println(this)

            println("    sources:")
            allSource.forEach {
                println("      source file: ${it.absolutePath}")
                empty = false
            }
            if (empty) println("      empty")

            empty = true
            println("    outputs:")
            output.asFileTree.files.forEach {
                println("      output file: ${it.absolutePath}")
                empty = false
            }
            if (empty) println("      empty")
        }
    }
}

fun javaProjectName() : String {
    return "'${project.name}' (Java project):"
}

