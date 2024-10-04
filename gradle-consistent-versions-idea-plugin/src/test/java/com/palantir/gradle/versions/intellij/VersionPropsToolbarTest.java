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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VersionPropsToolbarTest {

    @Test
    public void testGetInstance() {
        VersionPropsToolbar toolbar1 = new VersionPropsToolbar();
        VersionPropsToolbar toolbar2 = VersionPropsToolbar.getInstance();
        assertThat(toolbar1).isEqualTo(toolbar2);
    }

    @Test
    public void testGetAutoHideable() {
        VersionPropsToolbar toolbar = new VersionPropsToolbar();
        assertThat(toolbar.getAutoHideable()).isFalse();
    }

    @Test
    public void testRegister() {
        VersionPropsToolbar toolbar = new VersionPropsToolbar();
        DataContext dataContext = mock(DataContext.class);
        FloatingToolbarComponent component = mock(FloatingToolbarComponent.class);
        Disposable parentDisposable = mock(Disposable.class);
        FileEditor fileEditor = mock(FileEditor.class);
        Project project = mock(Project.class);
        VirtualFile virtualFile = mock(VirtualFile.class);

        when(dataContext.getData(PlatformDataKeys.FILE_EDITOR)).thenReturn(fileEditor);
        when(dataContext.getData(PlatformDataKeys.PROJECT)).thenReturn(project);
        when(fileEditor.getFile()).thenReturn(virtualFile);
        when(virtualFile.getPath()).thenReturn("test-path");

        toolbar.register(dataContext, component, parentDisposable);

        Map<Project, Map<String, FloatingToolbarComponent>> projectFilesToolbarComponents =
                toolbar.getProjectFilesToolbarComponents();
        assertThat(projectFilesToolbarComponents).containsKey(project);
        assertThat(projectFilesToolbarComponents.get(project)).containsKey("test-path");
    }

    @Test
    public void testHideToolbarForFile() {
        VersionPropsToolbar toolbar = new VersionPropsToolbar();
        Project project = mock(Project.class);
        Editor editor = mock(Editor.class);
        Document document = mock(Document.class);
        FloatingToolbarComponent component = mock(FloatingToolbarComponent.class);

        when(editor.getDocument()).thenReturn(document);
        when(document.getText()).thenReturn("new");

        Map<String, FloatingToolbarComponent> fileToolbarMap = new HashMap<>();
        fileToolbarMap.put("test-path", component);
        toolbar.getProjectFilesToolbarComponents().put(project, fileToolbarMap);

        Map<String, String> fileContentMap = new HashMap<>();
        fileContentMap.put("test-path", "original");
        toolbar.getProjectOriginalContent().put(project, fileContentMap);

        toolbar.hideToolbarForFile("test-path", project, Optional.of(editor));

        Mockito.verify(component).scheduleHide();
        assertThat(toolbar.getProjectOriginalContent().get(project).get("test-path"))
                .isEqualTo("new");
    }
}
