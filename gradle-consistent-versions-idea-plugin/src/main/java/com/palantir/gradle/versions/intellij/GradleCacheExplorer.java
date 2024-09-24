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

import com.google.common.base.Splitter;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GradleCacheExplorer {
    public final List<Folder> getFolders(DependencyGroup group) {
        String gradleCachePath = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1";
        Map<Folder, Object> tree = new HashMap<>();
        buildGradleCacheTreeFromFolder(new File(gradleCachePath), tree);
        return searchGradleCacheTree(tree, group.parts());
    }

    private static void buildGradleCacheTreeFromFolder(File folder, Map<Folder, Object> tree) {
        if (folder.exists() && folder.isDirectory()) {
            for (File subFolder : Objects.requireNonNull(folder.listFiles())) {
                if (subFolder.isDirectory()) {
                    String folderName = subFolder.getName();
                    Iterable<String> parts = Splitter.on('.').split(folderName);
                    Map<Folder, Object> currentLevel = tree;
                    for (String part : parts) {
                        currentLevel  = (Map<Folder, Object>) currentLevel.computeIfAbsent(Folder.of(part), k -> new HashMap<Folder, Object>());
                    }
                    for (File subSubFolder : Objects.requireNonNull(subFolder.listFiles())) {
                        if (subSubFolder.isDirectory()) {
                            String subFolderName = subSubFolder.getName();
                            currentLevel.put(Folder.of(subFolderName), new HashMap<Folder, Object>());
                        }
                    }
                }
            }
        }
    }

    private static List<Folder> searchGradleCacheTree(Map<Folder, Object> tree, List<String> parts) {
        Map<Folder, Object> currentLevel = tree;
        for (String part : parts) {
            if (currentLevel.containsKey(Folder.of(part))) {
                currentLevel = (Map<Folder, Object>) currentLevel.get(Folder.of(part));
            } else {
                return null;
            }
        }
        return new ArrayList<>(currentLevel.keySet());
    }
}
