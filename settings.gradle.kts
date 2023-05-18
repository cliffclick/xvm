rootProject.name = "xvm"

pluginManagement {
    // Include 'plugins build' to define convention plugins. (incubating, but new best practice to avoid complete
    // recompilations of the project when using buildSrc. While buildSrc is implicit as a separate build, we have
    // to explicitly use "includeBuild" to pick up the build-logic folder.
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral {
            content {
                excludeGroup("org.xtclang.xvm")
            }
        }
        gradlePluginPortal()
    }
}

fun discoverSubprojects() {
    println("Looking for Gradle aware subprojects:")
    rootDir.walk().forEach {
        if (it.isFile && "build.gradle.kts".equals(it.name)) {
            println("  subproject directory: '${it.parent}'")
            // TODO: Automatically include these, instead of explicitly specifying them below.
        }
    }
}

discoverSubprojects()

include(":javatools_utils")     // produces javatools_utils.jar for org.xvm.utils package
include(":javatools_unicode")   // produces data files -> :lib_ecstasy/resources, only on request
include(":javatools")           // produces javatools.jar
include(":javatools_turtle")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_bridge")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_launcher")  // produces native executables (Win, Mac, Linux), only on request
include(":lib_ecstasy")         // produces *only* a source zip file (no .xtc), and only on request
include(":lib_aggregate")       // produces aggregate.xtc
include(":lib_collections")     // produces collections.xtc
include(":lib_crypto")          // produces crypto.xtc
include(":lib_net")             // produces net.xtc
include(":lib_json")            // produces json.xtc
include(":lib_oodb")            // produces oodb.xtc
include(":lib_imdb")            // produces imdb.xtc
include(":lib_jsondb")          // produces jsondb.xtc
include(":lib_web")             // produces web.xtc
include(":lib_webauth")         // produces webauth.xtc
include(":lib_xenia")           // produces xenia.xtc
include(":xdk")                 // builds the above modules (ecstasy.xtc, javatools_bridge.xtc, json.xtc, etc.)
include(":manualTests")         // TODO: Temporary; allowing gradle test execution
//include(":wiki")              // TODO: Implement the wiki generation task.
include(":plugin_xtc")
