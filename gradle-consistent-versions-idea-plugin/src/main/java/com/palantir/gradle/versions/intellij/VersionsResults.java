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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
public abstract class VersionsResults {

    public abstract List<Set<DependencyVersion>> alreadyLoadedVersions();

    public abstract List<CompletableFuture<Set<DependencyVersion>>> stillLoadingVersions();

    public final boolean isAllComplete() {
        return getIncompleteFutures().isEmpty();
    }

    public final boolean hasNoVersions() {
        return isAllComplete() && getVersionCounts().isEmpty();
    }

    public final Map<DependencyVersion, Long> getVersionCounts() {
        return alreadyLoadedVersions().stream()
                .collect(Collectors.flatMapping(
                        Set::stream, Collectors.groupingBy(Function.identity(), Collectors.counting())));
    }

    public final void scheduleRunnableOnCompletion(Runnable runnable) {
        List<CompletableFuture<?>> pendingFutures = getIncompleteFutures();
        if (!pendingFutures.isEmpty()) {
            CompletableFuture.anyOf(pendingFutures.toArray(new CompletableFuture[0]))
                    .thenRun(runnable);
        }
    }

    private List<CompletableFuture<?>> getIncompleteFutures() {
        return stillLoadingVersions().stream()
                .filter(future -> !future.isDone())
                .collect(Collectors.toList());
    }

    public static VersionsResults of(
            List<Set<DependencyVersion>> alreadyLoaded, List<CompletableFuture<Set<DependencyVersion>>> stillLoading) {
        return ImmutableVersionsResults.builder()
                .alreadyLoadedVersions(alreadyLoaded)
                .stillLoadingVersions(stillLoading)
                .build();
    }
}
