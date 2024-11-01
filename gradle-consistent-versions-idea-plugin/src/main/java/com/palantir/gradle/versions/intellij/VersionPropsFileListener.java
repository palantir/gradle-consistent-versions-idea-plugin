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

import com.google.common.collect.MapMaker;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionPropsFileListener implements AsyncFileListener {
    private static final Logger log = LoggerFactory.getLogger(VersionPropsFileListener.class);
    private static final String TASK_NAME = "writeVersionsLock";

    // Shared state to track changes per project
    private final ConcurrentMap<Project, ChangeFlags> projectChangeMap =
            new MapMaker().weakKeys().makeMap();

    // Executor for debouncing
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long DEBOUNCE_DELAY_MS = 350;

    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        List<VFileContentChangeEvent> versionPropsEvents = events.stream()
                .filter(event -> event instanceof VFileContentChangeEvent)
                .map(event -> (VFileContentChangeEvent) event)
                .filter(event -> "versions.props".equals(event.getFile().getName()))
                .toList();

        List<VFileContentChangeEvent> versionLockEvents = events.stream()
                .filter(event -> event instanceof VFileContentChangeEvent)
                .map(event -> (VFileContentChangeEvent) event)
                .filter(event -> "versions.lock".equals(event.getFile().getName()))
                .toList();

        if (versionPropsEvents.isEmpty() && versionLockEvents.isEmpty()) {
            return null;
        }

        List<Project> allOpenProjects = Arrays.stream(
                        ProjectManager.getInstance().getOpenProjects())
                .filter(Project::isInitialized)
                .filter(Predicate.not(ComponentManager::isDisposed))
                .toList();

        // Update the shared state based on current events
        allOpenProjects.forEach(project -> {
            String basePath = project.getBasePath();
            boolean hasPropsChange = versionPropsEvents.stream()
                    .anyMatch(event ->
                            event.getPath().startsWith(basePath) && !isFileMalformed(project, event.getFile()));
            boolean hasLockChange =
                    versionLockEvents.stream().anyMatch(event -> event.getPath().startsWith(basePath));

            if (hasPropsChange || hasLockChange) {
                projectChangeMap.compute(project, (proj, flags) -> {
                    if (flags == null) {
                        return ChangeFlags.of(hasPropsChange, hasLockChange);
                    } else {
                        return ChangeFlags.of(
                                flags.hasPropsChange() || hasPropsChange, flags.hasLockChange() || hasLockChange);
                    }
                });
            }
        });

        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                // Schedule processing after VFS changes have been applied
                projectChangeMap.keySet().forEach(project -> {
                    scheduler.schedule(() -> processChanges(project), DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
                });
            }
        };
    }

    private void processChanges(Project project) {
        ChangeFlags flags = projectChangeMap.remove(project);
        if (flags == null) {
            return;
        }

        if (flags.hasPropsChange && flags.hasLockChange) {
            // Both changes detected; do not process
            log.debug(
                    "Project {} has both versions.props and versions.lock changes. Skipping processing.",
                    project.getName());
            return;
        }

        if (flags.hasPropsChange) {
            // Only props changed; process the project
            VersionPropsProjectSettings settings = VersionPropsProjectSettings.getInstance(project);
            if (!settings.isEnabled()) {
                return;
            }

            if (hasBuildSrc(project)) {
                runTaskThenRefresh(project);
            } else {
                refreshProjectWithTask(project);
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

    private static boolean isFileMalformed(Project project, VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

        if (psiFile == null || !(psiFile.getFileType() instanceof VersionPropsFileType)) {
            return true;
        }

        return PsiTreeUtil.hasErrorElements(psiFile);
    }

    private record ChangeFlags(boolean hasPropsChange, boolean hasLockChange) {
        public static ChangeFlags of(boolean hasPropsChange, boolean hasLockChange) {
            return new ChangeFlags(hasPropsChange, hasLockChange);
        }
    }
}
