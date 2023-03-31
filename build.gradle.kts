/*
 * Main build file for the XVM project, producing the XDK.
 */

group   = "org.xvm"
version = "0.4.3"

allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils"  )).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools"        )).using(project(":javatools"))
        }
    }

    repositories {
        mavenCentral {
            content {
                excludeGroup("org.xtclang.xvm")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register("build") {
    group       = "Build"
    description = "Build all projects"

    dependsOn(project("xdk:").tasks["build"])
}

/*
 * Generic helper tasks below, e.g. cleaning up all generated files not under source control
 *
 * Note that "gradle clean" flushes dependencies and things from caches and daemons, but does
 * not get rid of a physical build on disk like e.g. "make clean" typically does.
 */

/*
 * gitClean:
 *
 * This is a task to clean up all files in the source tree that are not under source control,
 * with the exception of individual IDE configurations.
 *
 * This task is a dry run; it will only list what it would like to delete, and it's recommended
 * to run this first as a safety measure. To actually perform the deletions, use the task
 */
tasks.register<Exec>("gitClean") {
    group = "other"
    description = "Runs git clean, recursively from the repo root. The .idea directory is exempt. Use the -Pgit-clean property to delete fiels for real."

    val flagsDryRun = "-nfxd"
    val flagsRealRun = "-fxd"

    val prop = (project.findProperty("gitCleanDryRun") ?: "true").toString()

    println("Running gitClean task...")
    val flags = if ("true".equals(prop, ignoreCase = true)) flagsDryRun else flagsRealRun
    if (flags == flagsDryRun) {
      println("  This is a dry run. To delete files for real, explicitly pass the property 'gitCleanDryRun=false'.")
    } else {
      println("  This is NOT a dry run. Delete actions will be performed.")
    }
    println("  Executing: 'git clean $flags -e .idea'")
    System.out.flush()
    commandLine("git", "clean", flags, "-e", ".idea")
    println("Done.")
}
