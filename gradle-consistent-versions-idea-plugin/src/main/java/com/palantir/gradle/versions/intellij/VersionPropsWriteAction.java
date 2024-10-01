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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionPropsWriteAction extends AnAction {
    private static final Logger log = LoggerFactory.getLogger(VersionPropsWriteAction.class);
    private static final String TASK_NAME = "writeVersionsLock";

    public VersionPropsWriteAction() {
        super("Write Versions Lock");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();

        if (editor != null && project != null) {
            if (hasBuildSrc(project)) {
                runTaskThenRefresh(project);
            } else {
                refreshProjectWithTask(project);
            }

            VirtualFile file = editor.getVirtualFile();
            if (file != null) {
                VersionPropsToolbar.getInstance().hideToolbarForFile(file.getPath(), project, editor);
            }
        }
    }

    private boolean hasBuildSrc(Project project) {
        return Files.exists(Paths.get(project.getBasePath(), "buildSrc"));
    }

    private void runTaskThenRefresh(Project project) {
        log.debug("Running task {} on project {}", TASK_NAME, project.getName());
        TaskCallback callback = new TaskCallback() {
            @Override
            public void onSuccess() {
                log.debug("Task {} successfully executed", TASK_NAME);
                refreshProject(project);
            }

            @Override
            public void onFailure() {
                log.error("Task {} failed", TASK_NAME);
            }
        };
        ExternalSystemTaskExecutionSettings settings = createExecutionSettings(project);
        ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
                callback,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC);
    }

    private ExternalSystemTaskExecutionSettings createExecutionSettings(Project project) {
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(project.getBasePath());
        settings.setTaskNames(Collections.singletonList(TASK_NAME));
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
        return settings;
    }

    private void refreshProjectWithTask(Project project) {
        log.debug("Refreshing project {} with task {}", project.getName(), TASK_NAME);
        refreshProject(project, new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).withArguments(TASK_NAME));
    }

    private void refreshProject(Project project) {
        refreshProject(project, new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID));
    }

    private void refreshProject(Project project, ImportSpecBuilder importSpec) {
        ExternalSystemUtil.refreshProject(project.getBasePath(), importSpec);
    }
}