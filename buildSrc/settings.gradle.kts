/**
 * buildSrc is already common build logic to all projects that need it, avoiding any duplication of
 *
 * One such common use is the artefacts or versions numbers that should be defined in one place only.
 * This place is preferred to tossing them into the root settings.gradle.kts, if we want buildSrc
 * support in some form for other things (and we do)
 *
 * We should also be able to use e.g. git actions, or RenovateBot to automatically upgrade
 * version catalog artefacts later.
 */

dependencyResolutionManagement {
    versionCatalogs {
        /**
         * Root project version and subproject versions
         */
        create("libs") {
            version("jdk", "17")
            version("xvm", "0.4.3")
            library("xvm-artifact", "org.xvm", "xvm").versionRef("xvm")

            /**
             * Junit tests.
             * TODO: Upgrade to Jupiter/JUnit 5
             */
            version("junit", "4.12")
            library("junit", "junit", "junit").versionRef("junit")

            /**
             * Dependencies needed for javatools_unicode, and generation
             */
            library("activation", "com.sun.activation:javax.activation:1.2.0")
            library("bind-api", "jakarta.xml.bind:jakarta.xml.bind-api:2.3.2")
            library("jaxb", "org.glassfish.jaxb:jaxb-runtime:2.3.2")
            bundle("unicode", listOf("activation", "bind-api", "jaxb"))
        }

        // In order to keep the artifact declarations and their versions without
        // any surrounding logic, we can move to this approach later, if we feel it
        // reduces complexity. The toml file with artifacts usually lives in the
        // rootProject/gradle folder.
        //create("libs") {
        //    from(files("../gradle/libs.versions.toml"))
        //}
    }
}
