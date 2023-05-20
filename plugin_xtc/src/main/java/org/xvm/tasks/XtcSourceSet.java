package org.xvm.tasks;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;

// TODO move to tasks
public interface XtcSourceSet {
    SourceDirectorySet getXtc();

    XtcSourceSet xtc(@DelegatesTo(SourceDirectorySet.class) Closure<?> configureClosure);

    XtcSourceSet xtc(Action<? super SourceDirectorySet> configureAction);

    SourceDirectorySet getAllXtc();
}
