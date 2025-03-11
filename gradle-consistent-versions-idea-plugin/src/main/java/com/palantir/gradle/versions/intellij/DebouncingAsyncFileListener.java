/*
 * (c) Copyright 2024 Palantir Technologies Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package com.palantir.gradle.versions.intellij;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebouncingAsyncFileListener implements AsyncFileListener {
    private static final Logger log = LoggerFactory.getLogger(DebouncingAsyncFileListener.class);

    private final AsyncFileListener delegate;
    private final BlockingQueue<VFileEvent> bufferedEvents = new LinkedBlockingQueue<>();

    private final int debounceDelayMillis;
    private volatile Future<?> scheduledFuture;

    DebouncingAsyncFileListener(AsyncFileListener delegate, int debounceDelayMillis) {
        this.delegate = delegate;
        this.debounceDelayMillis = debounceDelayMillis;
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        log.debug("Received events: {}", events);
        bufferedEvents.addAll(events);
        scheduleDebouncedProcessing();
        return null;
    }

    private synchronized void scheduleDebouncedProcessing() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture =
                JobScheduler.getScheduler().schedule(this::processEvents, debounceDelayMillis, TimeUnit.MILLISECONDS);
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
