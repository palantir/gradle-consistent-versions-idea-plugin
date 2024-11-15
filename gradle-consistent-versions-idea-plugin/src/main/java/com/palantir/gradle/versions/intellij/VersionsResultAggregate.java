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

import com.palantir.gradle.versions.intellij.VersionExplorer.AlreadyLoadedVersions;
import com.palantir.gradle.versions.intellij.VersionExplorer.StillLoadingVersions;
import com.palantir.gradle.versions.intellij.VersionExplorer.VersionsResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
public abstract class VersionsResultAggregate {

    public abstract List<VersionsResult> results();

    public final boolean isAllComplete() {
        return getIncompleteFutures().isEmpty();
    }

    public final boolean hasNoVersions() {
        return isAllComplete() && getVersionCounts().isEmpty();
    }

    public final Map<DependencyVersion, Long> getVersionCounts() {
        return results().stream()
                .filter(result -> result instanceof AlreadyLoadedVersions)
                .flatMap(av -> ((AlreadyLoadedVersions) av).versions().stream())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
    }

    public final void scheduleRefreshOnCompletion(Runnable refreshAction) {
        List<CompletableFuture<?>> pendingFutures = getIncompleteFutures();
        if (!pendingFutures.isEmpty()) {
            CompletableFuture.anyOf(pendingFutures.toArray(new CompletableFuture[0]))
                    .thenRun(refreshAction);
        }
    }

    private List<CompletableFuture<?>> getIncompleteFutures() {
        return results().stream()
                .filter(result -> result instanceof StillLoadingVersions)
                .map(sv -> ((StillLoadingVersions) sv).future())
                .filter(future -> !future.isDone())
                .collect(Collectors.toList());
    }

    public static VersionsResultAggregate of(List<VersionsResult> results) {
        return ImmutableVersionsResultAggregate.builder().results(results).build();
    }
}