import java.io.ByteArrayOutputStream

plugins {
    id("org.xvm.java-common-conventions")
}

val getJdkVersion: () -> String by extra

fun resolveIsCI() : String {
    return System.getenv("CI")
}

fun resolveBuildNum() : String {
    return System.getenv("BUILD_NUMBER")
}

fun resolveDistName() : String {
    val isCI = resolveIsCI()
    val buildNum = resolveBuildNum()

    println("Resolve distname: isCI=$isCI, buildNum=$buildNum")

    var distName = getJdkVersion()
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
