package org.xvm.internal;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.xvm.tasks.XtcSourceDirectorySet;

import javax.inject.Inject;

public class DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet implements XtcSourceDirectorySet {
    @Inject
    public DefaultXtcSourceDirectorySet(SourceDirectorySet sourceDirectorySet, TaskDependencyFactory taskDependencyFactory) {
        super(sourceDirectorySet, taskDependencyFactory);
    }
}
