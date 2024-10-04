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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VersionPropsToolbar extends AbstractFloatingToolbarProvider {
    private static VersionPropsToolbar instance;
    private final Map<Project, Map<String, FloatingToolbarComponent>> projectFilesToolbarComponents = new HashMap<>();
    private final Map<Project, Map<String, String>> projectOriginalContent = new HashMap<>();

    public VersionPropsToolbar() {
        super("VersionPropsActionGroup");
        instance = this;
    }

    public static VersionPropsToolbar getInstance() {
        return instance;
    }

    @Override
    public final boolean getAutoHideable() {
        return false;
    }

    @Override
    public final void register(
            DataContext dataContext, FloatingToolbarComponent component, Disposable parentDisposable) {
        super.register(dataContext, component, parentDisposable);

        FileEditor fileEditor = dataContext.getData(PlatformDataKeys.FILE_EDITOR);
        Project project = dataContext.getData(PlatformDataKeys.PROJECT);

        if (fileEditor == null || fileEditor.getFile() == null || project == null) {
            return;
        }

        projectFilesToolbarComponents.computeIfAbsent(project, k -> new HashMap<>());
        projectOriginalContent.computeIfAbsent(project, k -> new HashMap<>());

        registerDocumentListener(dataContext, fileEditor, parentDisposable, project);

        projectFilesToolbarComponents.get(project).put(fileEditor.getFile().getPath(), component);
    }

    private void registerDocumentListener(
            DataContext dataContext, FileEditor fileEditor, Disposable parentDisposable, Project project) {
        Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile file = fileEditor.getFile();
            if (file != null) {
                projectOriginalContent
                        .get(project)
                        .put(file.getPath(), editor.getDocument().getText());
            }
            editor.getDocument()
                    .addDocumentListener(
                            new VersionPropsDocumentListener(
                                    fileEditor,
                                    editor,
                                    projectOriginalContent.get(project),
                                    projectFilesToolbarComponents.get(project)),
                            parentDisposable);
        }
    }

    public final void hideToolbarForFile(String filePath, Project project, Optional<Editor> editor) {
        FloatingToolbarComponent toolbarComponent = projectFilesToolbarComponents
                .getOrDefault(project, new HashMap<>())
                .get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleHide();
        }

        editor.ifPresent(value -> projectOriginalContent
                .get(project)
                .put(filePath, value.getDocument().getText()));
    }

    // getter methods used for tests
    public final Map<Project, Map<String, FloatingToolbarComponent>> getProjectFilesToolbarComponents() {
        return projectFilesToolbarComponents;
    }

    public final Map<Project, Map<String, String>> getProjectOriginalContent() {
        return projectOriginalContent;
    }
}
