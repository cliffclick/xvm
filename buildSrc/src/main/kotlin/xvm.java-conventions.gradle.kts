/**
 * There is typically one convention per type of project. This convention handles generic Java projects, and
 * common build logic for all of them.
 *
 * Typically, the only thing a Java project should need to do, is to start out with:
 *
 * plugins {
 *     id("xvm.java-conventions")
 * }
 */

plugins {
    java
}

val ver =

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs = listOf("-Xlint:unchecked")
}

/*
dependencies {
    testImplementation(libs.versions.junit)
}
*/
