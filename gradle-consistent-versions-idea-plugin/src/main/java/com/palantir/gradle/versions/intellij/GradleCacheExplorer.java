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
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GradleCacheExplorer {

    private static final String GRADLE_CACHE_PATH =
            System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1";
    private static final Cache<String, Set<String>> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    public final Set<String> getCompletions(DependencyGroup group) {
        Set<String> cacheSet = cache.get(GRADLE_CACHE_PATH, key -> buildCacheSet(new File(GRADLE_CACHE_PATH)));
        return searchCacheSet(cacheSet, String.join(".", group.parts()));
    }

    static Set<String> searchCacheSet(Set<String> input, String pattern) {
        if (pattern.isEmpty()) {
            return input;
        }
        Set<String> resultSet = new HashSet<>();
        for (String str : input) {
            if (str.startsWith(pattern)) {
                resultSet.add(str.substring(pattern.length() + 1));
            }
        }
        return resultSet;
    }

    static Set<String> buildCacheSet(File folder) {
        Set<String> cacheSet = new HashSet<>();
        if (!folder.exists() || !folder.isDirectory()) {
            return cacheSet;
        }
        addFoldersToSet(folder, "", cacheSet);
        return cacheSet;
    }

    private static void addFoldersToSet(File folder, String parentPath, Set<String> cacheSet) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String folderPath = parentPath.isEmpty() ? file.getName() : parentPath + "." + file.getName();
                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        for (File subFile : subFiles) {
                            if (subFile.isDirectory()) {
                                String subFolderPath = folderPath + ":" + subFile.getName();
                                cacheSet.add(subFolderPath);
                            }
                        }
                    }
                }
            }
        }
    }
}
