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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Optional;

public class VersionPropsToolbar extends AbstractFloatingToolbarProvider {
    private static final String FILE_NAME = "versions.props";

    public VersionPropsToolbar() {
        super("VersionPropsActionGroup");
    }

    @Override
    public boolean getAutoHideable() {
        return false;
    }

    @Override
    public void register(DataContext dataContext, FloatingToolbarComponent component, Disposable parentDisposable) {
        //        look at
        // https://github.com/yunyizhi/clion-platformio-plus/blob/18cab368c63f516a9b12af00450d4f28cbd07b13/src/main/java/org/btik/platformioplus/ini/reload/PlatformioIniFloatingToolbarProvider.java
        super.register(dataContext, component, parentDisposable);

        Project project = dataContext.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            return;
        }
    }

    @Override
    public boolean isApplicable(DataContext dataContext) {
        return Optional.ofNullable(dataContext.getData(CommonDataKeys.EDITOR))
                .map(Editor::getVirtualFile)
                .map(VirtualFile::getName)
                .map(FILE_NAME::equals)
                .orElse(false);
    }
}