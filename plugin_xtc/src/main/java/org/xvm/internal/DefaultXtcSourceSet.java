/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xvm.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.xvm.tasks.XtcSourceDirectorySet;
import org.xvm.tasks.XtcSourceSet;

import javax.inject.Inject;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.internal.ConfigureUtil.configure;
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

@Deprecated
public abstract class DefaultXtcSourceSet implements XtcSourceSet, HasPublicType {
    private final XtcSourceDirectorySet xtc;
    private final SourceDirectorySet allXtc;

    @Inject
    public DefaultXtcSourceSet(final String displayName, final ObjectFactory objectFactory) {
        this.xtc = createXtcSourceDirectorySet("xtc", displayName + " XTC source", objectFactory);
        this.allXtc = objectFactory.sourceDirectorySet("allxtc", displayName + " XTC source");
        allXtc.getFilter().include("**/*.x");
        allXtc.source(xtc);
    }

    private static XtcSourceDirectorySet createXtcSourceDirectorySet(String name, String displayName, ObjectFactory objectFactory) {
        XtcSourceDirectorySet xtcSourceDirectorySet = objectFactory.newInstance(DefaultXtcSourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName));
        xtcSourceDirectorySet.getFilter().include("**/*.x");
        return xtcSourceDirectorySet;
    }

    @Override
    public XtcSourceDirectorySet getXtc() {
        return xtc;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public XtcSourceSet xtc(Closure configureClosure) {
        configure(configureClosure, getXtc());
        return this;
    }

    @Override
    public XtcSourceSet xtc(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getXtc());
        return this;
    }

    @Override
    public SourceDirectorySet getAllXtc() {
        return allXtc;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(XtcSourceSet.class);
    }
}
