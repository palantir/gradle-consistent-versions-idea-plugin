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

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectRefreshUtils {
    private static final Logger log = LoggerFactory.getLogger(ProjectRefreshUtils.class);

    private ProjectRefreshUtils() {
        // Utility class
    }

    public static void runWriteVersionsLock(Project project) {
        String taskName = "writeVersionsLock";
        if (hasBuildSrc(project)) {
            runTaskThenRefresh(project, taskName);
        } else {
            refreshProjectWithTask(project, taskName);
        }
    }

    private static void runTaskThenRefresh(Project project, String taskName) {
        log.debug("Running task {} on project {}", taskName, project.getName());
        TaskCallback callback = new TaskCallback() {
            @Override
            public void onSuccess() {
                log.debug("Task {} successfully executed", taskName);
                refreshProject(project);
            }

            @Override
            public void onFailure() {
                log.error("Task {} failed", taskName);
            }
        };
        ExternalSystemTaskExecutionSettings settings = createExecutionSettings(project, taskName);
        ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
                callback,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC);
    }

    private static ExternalSystemTaskExecutionSettings createExecutionSettings(Project project, String taskName) {
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(project.getBasePath());
        settings.setTaskNames(Collections.singletonList(taskName));
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
        return settings;
    }

    private static void refreshProjectWithTask(Project project, String taskName) {
        log.debug("Refreshing project {} with task {}", project.getName(), taskName);
        refreshProject(project, new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).withArguments(taskName));
    }

    private static void refreshProject(Project project) {
        refreshProject(project, new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID));
    }

    private static void refreshProject(Project project, ImportSpecBuilder importSpec) {
        ExternalSystemUtil.refreshProject(project.getBasePath(), importSpec);
    }

    private static boolean hasBuildSrc(Project project) {
        return Files.exists(Paths.get(project.getBasePath(), "buildSrc"));
    }
}
