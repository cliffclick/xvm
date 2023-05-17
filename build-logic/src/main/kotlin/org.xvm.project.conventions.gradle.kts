import java.io.ByteArrayOutputStream

val versionCatalog by extra(extensions.getByType<VersionCatalogsExtension>().named("libs"))
val jdkVersion by extra(resolveJdkVersion())
val xvmVersion by extra(resolveXvmVersion())
val defaultJdkVersion = 17

var getDistributionName by extra {
    fun() : String {
        val isCI = resolveIsCI()
        val buildNum = resolveBuildNum()
        val jdkVersion = jdkVersion

        println("Resolve distName: isCI=$isCI, buildNum=$buildNum, jdkVersion=$jdkVersion")

        var distName = jdkVersion
        if (isCI != null && isCI != "0" && !"false".equals(isCI, ignoreCase = true) && buildNum != null) {
            distName += "ci$buildNum"
            val output = ByteArrayOutputStream()
            project.exec {
                commandLine("git", "rev-parse", "HEAD")
                standardOutput = output
                isIgnoreExitValue = true
            }
            val changeId : String = output.toString().trim()
            if (changeId.isNotEmpty()) {
                distName += "+$changeId"
            }
        }
        return distName
    }
}

fun resolveJdkVersion() : String {
    return versionCatalogLookupVersion("jdk", defaultJdkVersion)
}

fun resolveXvmVersion() : String {
    return versionCatalogLookupVersion("xvm")
}

fun resolveIsCI() : String? {
    return System.getenv("CI")
}

fun resolveBuildNum() : String? {
    return System.getenv("BUILD_NUMBER")
}

fun versionCatalogLookupLibrary(name : String): Provider<MinimalExternalModuleDependency> {
    return versionCatalog.findLibrary(name).get()
}

fun versionCatalogLookupVersion(name : String, defaultValue : Any? = null): String {
    val version = versionCatalog.findVersion(name)
    if (version.isPresent) { // TODO: There has to be a pretty Kotlin construct for this Java style Optional
        return version.get().toString()
    }
    if (defaultValue == null) {
        throw NoSuchElementException(name)
    }
    return defaultValue.toString()
}
