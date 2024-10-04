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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

public class VersionPropsDocumentListener implements DocumentListener {
    private final FileEditor fileEditor;
    private final Editor editor;
    private final Map<String, String> originalContent;
    private final Map<String, FloatingToolbarComponent> filesToolbarComponents;
    private static final String FILE_NAME = "versions.props";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public VersionPropsDocumentListener(
            FileEditor fileEditor,
            Editor editor,
            Map<String, String> originalContent,
            Map<String, FloatingToolbarComponent> filesToolbarComponents) {
        this.fileEditor = fileEditor;
        this.editor = editor;
        this.originalContent = originalContent;
        this.filesToolbarComponents = filesToolbarComponents;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {}

    @Override
    public final void documentChanged(DocumentEvent event) {
        VirtualFile file = fileEditor.getFile();
        if (file != null && FILE_NAME.equals(file.getName())) {
            VersionPropsProjectSettings projectSettings =
                    VersionPropsProjectSettings.getInstance(Objects.requireNonNull(editor.getProject()));
            VersionPropsAppSettings appSettings = VersionPropsAppSettings.getInstance();
            if (!projectSettings.isEnabled() || appSettings.isEnabled()) {
                scheduleUpdate(() -> updateFileUnchanged(file.getPath()));
                return;
            }

            String currentContent = editor.getDocument().getText();

            // This requires debouncing to so that the toolbar actually shows up
            if (!originalContent.get(file.getPath()).equals(currentContent)) {
                scheduleUpdate(() -> updateFileChanged(file.getPath()));
            } else {
                scheduleUpdate(() -> updateFileUnchanged(file.getPath()));
            }
        }
    }

    private void scheduleUpdate(Runnable updateTask) {
        scheduler.schedule(() -> SwingUtilities.invokeLater(updateTask), 300, TimeUnit.MILLISECONDS);
    }

    private void updateFileChanged(String filePath) {
        FloatingToolbarComponent toolbarComponent = filesToolbarComponents.get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleShow();
        }
    }

    private void updateFileUnchanged(String filePath) {
        FloatingToolbarComponent toolbarComponent = filesToolbarComponents.get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleHide();
        }
    }
}
