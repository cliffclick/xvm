rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()    // Use the plugin portal to apply community plugins in convention plugins.
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
