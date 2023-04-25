/*
 * Build file for the Unicode tools portion of the XDK.
 *
 * Technically, this only needs to be built and run when new versions of the Unicode standard are
 * released, and when that occurs, the code in Char.x also has to be updated (to match the .dat file
 * data) using the values in the *.txt files that are outputted by running this.
 */

plugins {
    id("org.xvm.java-application-conventions")
}

dependencies {
    implementation(libs.bundles.unicode)
    implementation("org.xtclang.xvm:javatools_utils:")
}

application {
    mainClass.set("org.xvm.tool.BuildUnicodeTables")
}
