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

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public final class VersionPropsFileListener implements AsyncFileListener {

    @Nullable
    @Override
    public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
        List<VFileContentChangeEvent> versionPropsEvents = events.stream()
                .filter(event -> event instanceof VFileContentChangeEvent)
                .map(event -> (VFileContentChangeEvent) event)
                .filter(event -> "versions.props".equals(event.getFile().getName()))
                .toList();

        if (versionPropsEvents.isEmpty()) {
            return null;
        }

        List<Project> projectsAffected = Arrays.stream(
                        ProjectManager.getInstance().getOpenProjects())
                .filter(Project::isInitialized)
                .filter(Predicate.not(ComponentManager::isDisposed))
                .filter(project -> versionPropsEvents.stream()
                        .anyMatch(event -> event.getPath().startsWith(project.getBasePath())
                                && !isFileMalformed(project, event.getFile())))
                .toList();

        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                projectsAffected.forEach(project -> {
                    VersionPropsProjectSettings settings = VersionPropsProjectSettings.getInstance(project);
                    VersionPropsAppSettings appSettings = VersionPropsAppSettings.getInstance();
                    if (!settings.isEnabled() || !appSettings.isEnabled()) {
                        return;
                    }
                    ProjectRefreshUtils.runWriteVersionsLock(project);
                });
            }
        };
    }

    private static boolean isFileMalformed(Project project, VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

        if (psiFile == null || !(psiFile.getFileType() instanceof VersionPropsFileType)) {
            return true;
        }

        return PsiTreeUtil.hasErrorElements(psiFile);
    }
}
