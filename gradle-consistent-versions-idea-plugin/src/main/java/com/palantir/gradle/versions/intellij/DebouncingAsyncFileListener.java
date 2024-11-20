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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebouncingAsyncFileListener implements AsyncFileListener {
    private static final Logger log = LoggerFactory.getLogger(DebouncingAsyncFileListener.class);

    private final AsyncFileListener delegate;
    private final SingleAlarm alarm;
    private final BlockingQueue<VFileEvent> bufferedEvents = new LinkedBlockingQueue<>();

    DebouncingAsyncFileListener(AsyncFileListener delegate, int debounceDelayMillis, Disposable parentDisposable) {
        this.delegate = delegate;
        this.alarm = new SingleAlarm(
                this::processEvents, debounceDelayMillis, parentDisposable, Alarm.ThreadToUse.POOLED_THREAD);
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        log.debug("Received events: {}", events);
        bufferedEvents.addAll(events);
        alarm.request();
        return null;
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    private void processEvents() {
        List<VFileEvent> eventsToProcess = new ArrayList<>();
        int drained = bufferedEvents.drainTo(eventsToProcess);
        if (drained == 0) {
            return;
        }

        log.debug("Processing debounced events: {}", eventsToProcess);
        AsyncFileListener.ChangeApplier applier = delegate.prepareChange(eventsToProcess);
        if (applier != null) {
            applier.afterVfsChange();
        }
    }
}
