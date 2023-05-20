/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    id("org.xvm.java-library-conventions")
}

// TODO: Required? Probably not - the from dependency in the jar task is explicit?
dependencies {
    implementation(project(":javatools_utils"))
}

// Dependencies that must be sorted
//    lib_ectasy:property.implicit.x - needs to be copied into our resource dir

tasks.register<Copy>("copyImplicits") {
    group = "Build"
    description = "Copy the implicit.x from :lib_ecstasy project into the build directory."
    from(file(project(":lib_ecstasy").property("implicit.x")!!))
    into(file("$buildDir/resources/main/"))
    doLast {
        println("Finished task: copyImplicits")
    }
}

// TODO We just want to get the lib_xtc implicit.x in our resource folder

tasks.jar {
    //from(project(":lib_ecstasy").sourceSets["main"].resources.srcDirs)
    val copyImplicits = tasks["copyImplicits"]
    from(project(":javatools_utils").sourceSets["main"].output)

    dependsOn(copyImplicits)

    mustRunAfter(copyImplicits)

    val version = libs.versions.xvm
    assert(rootProject.version.equals(version))

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

// TODO: required? probably not?
// Add the resource folder with implicits to the test source set instead.
tasks.compileTestJava {
    dependsOn(tasks["copyImplicits"])
}

dependencies {
    implementation("org.xtclang.xvm:javatools_utils:")
}
