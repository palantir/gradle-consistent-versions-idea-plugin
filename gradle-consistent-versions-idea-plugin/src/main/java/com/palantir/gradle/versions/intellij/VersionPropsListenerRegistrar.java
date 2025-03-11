/*
 * (c) Copyright 2024 Palantir Technologies Inc.
 * Licensed under the Apache License, Version 2.0.
 */

package com.palantir.gradle.versions.intellij;

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class VersionPropsListenerRegistrar implements AsyncFileListener {

    private final FilteringAsyncFileListener changeListener;

    VersionPropsListenerRegistrar() {
        this.changeListener = new FilteringAsyncFileListener(
                new DebouncingAsyncFileListener(new VersionPropsFileListener(), 250), this::isRelevantFile);
    }

    private boolean isRelevantFile(VirtualFile virtualFile) {
        String fileName = virtualFile.getName();
        return "versions.props".equals(fileName) || "versions.lock".equals(fileName);
    }

    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        return changeListener.prepareChange(events);
    }
}
