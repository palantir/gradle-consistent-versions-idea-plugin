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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Splitter;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GradleCacheExplorer {

    private static final String GRADLE_CACHE_PATH =
            System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1";
    private static final Cache<String, Map<Folder, Object>> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    public final List<Folder> getFolders(DependencyGroup group) {
        Map<Folder, Object> tree = cache.get(GRADLE_CACHE_PATH, key -> {
            Map<Folder, Object> newTree = new HashMap<>();
            buildGradleCacheTree(new File(GRADLE_CACHE_PATH), newTree);
            return newTree;
        });

        return searchGradleCacheTree(tree, group.parts());
    }

    private static void buildGradleCacheTree(File folder, Map<Folder, Object> tree) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }

        for (File subFolder : Objects.requireNonNull(folder.listFiles(File::isDirectory))) {
            String folderName = subFolder.getName();
            Map<Folder, Object> currentLevel = tree;

            for (String part : Splitter.on('.').split(folderName)) {
                currentLevel =
                        (Map<Folder, Object>) currentLevel.computeIfAbsent(Folder.of(part), k -> new HashMap<>());
            }

            for (File subSubFolder : Objects.requireNonNull(subFolder.listFiles(File::isDirectory))) {
                currentLevel.put(Folder.of(subSubFolder.getName()), new HashMap<>());
            }
        }
    }

    private static List<Folder> searchGradleCacheTree(Map<Folder, Object> tree, List<String> parts) {
        Map<Folder, Object> currentLevel = tree;

        for (String part : parts) {
            Folder folder = Folder.of(part);
            if (currentLevel.containsKey(folder)) {
                currentLevel = (Map<Folder, Object>) currentLevel.get(folder);
            } else {
                return Collections.emptyList();
            }
        }

        return List.copyOf(currentLevel.keySet());
    }
}
