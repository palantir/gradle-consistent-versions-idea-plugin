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
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.openapi.application.ApplicationManager;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefreshUtil {
    private static final Logger log = LoggerFactory.getLogger(RefreshUtil.class);

    public static Supplier<Void> refreshOnceSupplier() {
        return Suppliers.memoize(() -> {
            triggerRefresh();
            return null;
        });
    }

    private static void triggerRefresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            CompletionService completionService = CompletionService.getCompletionService();
            if (completionService == null) {
                return;
            }

            BaseCompletionService baseCompletionService = (BaseCompletionService) completionService;
            CompletionProcess completionProgress = baseCompletionService.getCurrentCompletion();
            if (completionProgress == null) {
                return;
            }

            CompletionProgressIndicator completionProgressIndicator = (CompletionProgressIndicator) completionProgress;
            log.debug("Scheduling restarting completion");
            completionProgressIndicator.scheduleRestart();
        });
    }

    private RefreshUtil() {}
}
