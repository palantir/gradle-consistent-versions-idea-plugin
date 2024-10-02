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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.Nullable;

@State(name = "VersionPropsPluginSettings", storages = @Storage("VersionPropsSettings.xml"))
public class VersionPropsSettingsState implements PersistentStateComponent<VersionPropsSettingsState> {
    private boolean featureEnabled = false;

    public static VersionPropsSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(VersionPropsSettingsState.class);
    }

    @Nullable
    @Override
    public VersionPropsSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(VersionPropsSettingsState state) {
        this.featureEnabled = state.featureEnabled;
    }

    public final boolean isFeatureEnabled() {
        return featureEnabled;
    }

    public final void setFeatureEnabled(boolean featureEnabled) {
        this.featureEnabled = featureEnabled;
    }
}