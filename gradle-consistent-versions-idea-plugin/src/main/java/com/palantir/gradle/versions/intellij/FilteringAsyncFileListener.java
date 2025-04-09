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

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilteringAsyncFileListener implements AsyncFileListener {
    private static final Logger log = LoggerFactory.getLogger(FilteringAsyncFileListener.class);

    private final AsyncFileListener delegate;
    private final Predicate<VirtualFile> filter;

    FilteringAsyncFileListener(AsyncFileListener delegate, Predicate<VirtualFile> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    @Nullable
    @Override
    public final ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        List<? extends VFileEvent> filteredEvents = events.stream()
                .filter(event -> {
                    VirtualFile file = event.getFile();
                    return file != null && filter.test(file);
                })
                .toList();

        log.debug("Events after filtering {}", filteredEvents);

        if (filteredEvents.isEmpty()) {
            return null;
        }

        return delegate.prepareChange(filteredEvents);
    }
}
