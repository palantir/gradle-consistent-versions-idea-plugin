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

import com.google.common.base.Suppliers;
import com.intellij.codeInsight.completion.BaseCompletionService;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.openapi.application.ApplicationManager;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompletionRefreshUtil {
    private static final Logger log = LoggerFactory.getLogger(CompletionRefreshUtil.class);

    public static Supplier<Void> refreshOnceSupplier() {
        return Suppliers.memoize(() -> {
            triggerRefresh();
            return null;
        });
    }

    @SuppressWarnings("for-rollout:ThrowSpecificExceptions")
    private static void triggerRefresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            CompletionService completionService = CompletionService.getCompletionService();
            if (completionService == null) {
                throw new IllegalStateException("Expected completionService to exist");
            }

            if (!(completionService instanceof BaseCompletionService baseCompletionService)) {
                throw new IllegalStateException(
                        "Expected completionService to be an instance of BaseCompletionService");
            }

            CompletionProcess completionProgress = baseCompletionService.getCurrentCompletion();
            if (completionProgress == null) {
                return;
            }

            // For completing versions, our needs are slightly different to "normal" completion. For normal completion,
            // you enter some text and generally have some idea about what you want to enter and the ordering of the
            // completions does not matter. For versions, the order really does matter - we want the latest to be
            // at the top and then the rest of the versions in descending order. Even changing the default sorter,
            // when you add new versions one by one, IntelliJ will often keep the same top 5 options at the top rather
            // than re-sorting them. This is exactly what we do not want. We can't wait until we've loaded all the
            // versions as this can take 10s of seconds as it involves doing network requests to Artifactory virtual
            // repos that can be very slow as they have many upstreams. So we refresh the completion ourselves each
            // time a new set of versions are loaded, which gives us the opportunity to add all the versions currently
            // loaded in the right order without IntelliJ messing with it.
            // To do this, we use an internal IntelliJ method to restart the completion. Imo this should be part of
            // the public API. Since we don't expect users to type anything for this "completion", we can't use the
            // restart rules in CompletionProcessBase#addWatchedPrefix without doing something horrible like entering
            // a special character and quickly deleting it. Unfortunately, to do this without the plugin being rejected
            // by the IntelliJ plugin analyser, we need to use reflection.
            log.debug("Scheduling restarting completion");
            try {
                completionProgress
                        .getClass()
                        .getDeclaredMethod("scheduleRestart")
                        .invoke(completionProgress);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletionRefreshUtil() {}
}
