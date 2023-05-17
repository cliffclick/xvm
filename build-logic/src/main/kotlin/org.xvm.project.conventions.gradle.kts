import java.io.ByteArrayOutputStream

// TODO: Project convention should really not care about Java if we can help it. It is the
//   other way around, really.
plugins {
    id("org.xvm.java-common-conventions")
}

val getXvmVersion by extra {
    fun() : String {
        return resolveXvmVersion()
    }
}

val getDistributionName by extra {
    fun() : String {
        val isCI = resolveIsCI()
        val buildNum = resolveBuildNum()

        println("Resolve distname: isCI=$isCI, buildNum=$buildNum")

        var distName = testGetter()
        if (isCI != null && isCI != "0" && !"false".equals(isCI, ignoreCase = true) && buildNum != null) {
            distName += "ci$buildNum"
            val output = ByteArrayOutputStream()
            project.exec {
                commandLine("git", "rev-parse", "HEAD")
                standardOutput = output
                isIgnoreExitValue = true
            }
            val changeId : String = output.toString().trim()
            if (!changeId.isEmpty()) {
                distName += "+$changeId"
            }
        }
        return distName
    }
}

fun resolveXvmVersion() : String {
    return "0.4.3"
}

fun testGetter() : String {
    println("THIS IS WRONG")
    return "17"
}

fun resolveIsCI() : String {
    return System.getenv("CI")
}

fun resolveBuildNum() : String {
    return System.getenv("BUILD_NUMBER")
}

