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
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebouncingAsyncFileListener implements AsyncFileListener, Disposable {
    private static final Logger log = LoggerFactory.getLogger(DebouncingAsyncFileListener.class);

    private final AsyncFileListener delegate;
    private final SingleAlarm alarm;
    private final Object lock = new Object();

    private final List<VFileEvent> bufferedEvents = new ArrayList<>();
    private boolean isDisposed = false;

    DebouncingAsyncFileListener(AsyncFileListener delegate, int debounceDelayMillis) {
        this.delegate = delegate;
        this.alarm =
                new SingleAlarm(this::processEvents, debounceDelayMillis, this, Alarm.ThreadToUse.POOLED_THREAD);
    }

    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        synchronized (lock) {
            if (isDisposed) {
                return null;
            }
            log.debug("Received events: {}", events);
            bufferedEvents.addAll(events);
            alarm.request();
        }
        return null;
    }

    private void processEvents() {
        List<VFileEvent> eventsToProcess;
        synchronized (lock) {
            if (isDisposed) {
                return;
            }
            eventsToProcess = new ArrayList<>(bufferedEvents);
            bufferedEvents.clear();
        }
        log.debug("Processing debounced events: {}", eventsToProcess);
        AsyncFileListener.ChangeApplier applier = delegate.prepareChange(eventsToProcess);
        if (applier != null) {
            applier.afterVfsChange();
        }
    }

    @Override
    public void dispose() {
        synchronized (lock) {
            if (isDisposed) {
                return;
            }
            isDisposed = true;
            alarm.cancelAllRequests();
        }
    }
}
