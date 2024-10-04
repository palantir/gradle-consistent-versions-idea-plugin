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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class VersionPropsAppSettingsPage implements Configurable {
    private JCheckBox enabledCheckbox;

    private final VersionPropsAppSettings settings;

    public VersionPropsAppSettingsPage() {
        settings = VersionPropsAppSettings.getInstance();
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Gradle Consistent Versions";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel rootPanel = new JPanel();
        enabledCheckbox = new JCheckBox("Enable writeVersionsLock on save");
        rootPanel.add(enabledCheckbox);
        return rootPanel;
    }

    @Override
    public boolean isModified() {
        return enabledCheckbox.isSelected() != settings.isEnabled();
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.setEnabled(enabledCheckbox.isSelected());
    }

    @Override
    public void reset() {
        enabledCheckbox.setSelected(settings.isEnabled());
    }

    @Override
    public void disposeUIResources() {
        // No resources to dispose
    }
}