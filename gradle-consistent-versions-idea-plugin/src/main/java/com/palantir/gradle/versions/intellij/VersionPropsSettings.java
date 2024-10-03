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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

@com.intellij.openapi.components.State(
        name = "GradleConsistentVersionsSettings",
        storages = {@Storage("gradle-consistent-versions-plugin-settings.xml")})
@Service(Service.Level.PROJECT)
public final class VersionPropsSettings implements PersistentStateComponent<VersionPropsSettings.State> {

    public enum EnabledState {
        UNKNOWN,
        ENABLED,
        DISABLED
    }

    public static final class State {
        private EnabledState enabled = EnabledState.UNKNOWN;
        private List<String> mavenRepositories = new ArrayList<>();

        public void setEnabled(@Nullable String enabledStr) {
            if (enabledStr == null) {
                enabled = EnabledState.UNKNOWN;
            } else if (Boolean.valueOf(enabledStr)) {
                enabled = EnabledState.ENABLED;
            } else {
                enabled = EnabledState.DISABLED;
            }
        }

        public String getEnabled() {
            return switch (enabled) {
                case ENABLED -> "true";
                case DISABLED -> "false";
                default -> null;
            };
        }

        @Override
        public String toString() {
            return "State{" + "enabled=" + enabled + ", mavenRepositories=" + mavenRepositories + '}';
        }

        public List<String> getMavenRepositories() {
            return mavenRepositories;
        }

        public void setMavenRepositories(List<String> mavenRepositories) {
            this.mavenRepositories = mavenRepositories;
        }
    }

    private State state = new State();

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(State status) {
        this.state = status;
    }

    public boolean isEnabled() {
        return state.enabled.equals(EnabledState.ENABLED);
    }

    public void setEnabled(boolean enabled) {
        setEnabled(enabled ? EnabledState.ENABLED : EnabledState.DISABLED);
    }

    public void setEnabled(EnabledState enabled) {
        state.enabled = enabled;
    }

    public List<String> getMavenRepositories() {
        return state.getMavenRepositories();
    }

    public void setMavenRepositories(List<String> mavenRepositories) {
        state.setMavenRepositories(mavenRepositories);
    }

    public static VersionPropsSettings getInstance(Project project) {
        return project.getService(VersionPropsSettings.class);
    }
}
