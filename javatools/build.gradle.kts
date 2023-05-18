/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    id("org.xvm.java-library-conventions")
}

sourceSets {
    main {
        resources {
            srcDirs += file("$buildDir/resources/main")
        }
    }
    test {
        resources {
            srcDirs += file("$buildDir/resources/main")
        }
    }
}

tasks.register<Copy>("copyImplicits") {
    group = "Build"
    description = "Copy the implicit.x from :lib_ecstasy project into the build directory."
    from(file(project(":lib_ecstasy").property("implicit.x")!!))
    into(file("$buildDir/resources/main/"))
    doLast {
        println("Finished task: copyImplicits")
    }
}

// Replaces the whole copy Implicits task.

tasks.register<Copy>("copyUtils") {
    group = "Build"
    description = "Copy the classes from :javatools_utils project into the build directory."
    dependsOn(project(":javatools_utils").tasks["classes"])
    from(file("${project(":javatools_utils").buildDir}/classes/java/main"))
    include("**/*.class")
    into(file("$buildDir/classes/java/main"))
    doLast {
        println("Finished task: copyUtils")
    }
}

// TODO We just want to get the lib_xtc implicit.x in our resource folder

tasks.jar {
    //from(project(":javatools_utils").sourceSets["main"].output.classesDirs)
    //from(project(":lib_ecstasy").sourceSets["main"].resources.srcDirs)
    val copyImplicits = tasks["copyImplicits"]
    val copyUtils = tasks["copyUtils"]

    dependsOn(copyImplicits)
    dependsOn(copyUtils)

    mustRunAfter(copyImplicits)
    mustRunAfter(copyUtils)

    val version = rootProject.version
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Main-Class"] = "org.xvm.tool.Launcher"
        attributes["Name"] = "/org/xvm/"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = version
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-prototype"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
}

tasks.compileTestJava {
    dependsOn(tasks["copyImplicits"])
    dependsOn(tasks["copyUtils"])
}

dependencies {
    implementation("org.xtclang.xvm:javatools_utils:")
}
