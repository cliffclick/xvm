package tasks

import org.gradle.api.Exec

class AbstractGitCleanTask(dryRun: Boolean) : Exec() {
    init {
        commandLine.executable = "git"
        commandLine.arguments = listOf("clean", "-e", ".idea", if (dryRun ?: true) "-nfxd" else "-fxd")
    }

    @TaskAction
    fun exec
        ExecAction execAction = getExecActionFactory().newExecAction();
        execSpec.copyTo(execAction);
        execResult.set(execAction.execute());
    }
}
