package org.xvm;

/**
 * Root source file for XTC language plugin. The XTC language plugin is a binary plugin, as per
 * best practice, since script plugins should never contain reusable logic.
 *
 * We also need the significantly more generic properties of Java for the XTC language
 * integration.
 *
 * The plugin aims to provide IntelliJ integration for everything about XTC, but should
 * probably also define the inner working/actual logic for the standard Gradle lifecycle tasks,
 * so that building a project written in XTC is no different than building one in Java.
 */

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

// TODO separate project.
// Blueprint: https://github.com/innobead/pygradle/blob/master/src/main/kotlin/com/innobead/gradle/task/PythonBuildTask.kt
public class XtcLangPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.task("javaTask");
    }
}