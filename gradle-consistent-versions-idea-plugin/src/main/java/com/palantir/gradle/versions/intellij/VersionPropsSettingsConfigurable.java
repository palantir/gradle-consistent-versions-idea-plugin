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
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class VersionPropsSettingsConfigurable implements Configurable {
    private JPanel panel;
    private JCheckBox featureCheckbox;

    @Nls
    @Override
    public String getDisplayName() {
        return "My Plugin Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (panel == null) {
            panel = new JPanel();
            featureCheckbox = new JCheckBox("Enable My Feature");
            panel.add(featureCheckbox);
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        return featureCheckbox.isSelected()
                != VersionPropsSettingsState.getInstance().isFeatureEnabled();
    }

    @Override
    public void apply() {
        VersionPropsSettingsState.getInstance().setFeatureEnabled(featureCheckbox.isSelected());
    }

    @Override
    public void reset() {
        featureCheckbox.setSelected(VersionPropsSettingsState.getInstance().isFeatureEnabled());
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        featureCheckbox = null;
    }
}