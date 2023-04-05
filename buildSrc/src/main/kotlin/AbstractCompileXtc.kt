package tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec

/**
 * Right now, only Groovy classes in buildSrc are universally available to the
 * build, not Kotlin ones. So the documentation is unclear. But Gradle seems to
 * support importing them, as a stop gap solution.
 *
 * Usage: task.register<CompileXtcTask>("path/to/source.x")
 */
abstract class AbstractCompileXtc : JavaExec() {
    @get:Input
    abstract val source: String

    lateinit var xdkVersion: String
    lateinit var libDir: String
    lateinit var libs: List<String>

    init {
        setGroup("Execution")
        jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")
        args("-o", libDir, "-version", xdkVersion)
        for (lib in libs) {
            args("-L", lib)
        }
        mainClass.set("org.xvm.tool.Compiler")
    }
}
