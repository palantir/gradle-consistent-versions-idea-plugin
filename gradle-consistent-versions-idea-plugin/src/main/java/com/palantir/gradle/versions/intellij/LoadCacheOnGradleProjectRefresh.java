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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class LoadCacheOnGradleProjectRefresh implements ExternalSystemTaskNotificationListener {
    private static final SafeLogger log = SafeLoggerFactory.get(LoadCacheOnGradleProjectRefresh.class);

    @Override
    public final void onSuccess(ExternalSystemTaskId id) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())
                && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
            log.debug("Gradle project refresh finished");
            GradleCacheExplorer.getInstance().loadCache();
        }
    }

    @Override
    public void onStart(ExternalSystemTaskId id, String workingDir) {}

    @Override
    public void onFailure(ExternalSystemTaskId id, Exception exception) {}

    @Override
    public void beforeCancel(ExternalSystemTaskId id) {}

    @Override
    public void onCancel(ExternalSystemTaskId id) {}

    @Override
    public void onStatusChange(ExternalSystemTaskNotificationEvent event) {}

    @Override
    public void onTaskOutput(ExternalSystemTaskId id, String text, boolean stdOut) {}

    @Override
    public void onEnd(ExternalSystemTaskId id) {}
}
