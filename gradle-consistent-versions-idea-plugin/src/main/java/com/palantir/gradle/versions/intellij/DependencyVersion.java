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

import org.immutables.value.Value;

@Value.Immutable
public abstract class DependencyVersion {
    protected abstract String version();

    protected abstract Boolean isLatest();

    public static ImmutableDependencyVersion of(String version, Boolean isLatest) {
        return ImmutableDependencyVersion.builder()
                .version(version)
                .isLatest(isLatest)
                .build();
    }

    @Override
    public final String toString() {
        return version();
    }
}
