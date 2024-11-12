/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.versions.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class VersionPropsListenerRegistrar implements AsyncFileListener, Disposable {

    private final FilteringAsyncFileListener changeListener;

    VersionPropsListenerRegistrar() {
        this.changeListener = new FilteringAsyncFileListener(
                new DebouncingAsyncFileListener(new VersionPropsFileListener(), 250, this), this::isRelevantFile);
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

    @Override
    public void dispose() {}
}
