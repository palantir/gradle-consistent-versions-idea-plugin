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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GradleCacheExplorerTest {
    private GradleCacheExplorer explorer;

    @BeforeEach
    public void before() {
        explorer = new GradleCacheExplorer();
    }

    @Test
    public void build_cache_returns_empty_for_empty_cache() throws IOException {
        Path tempDir = Files.createTempDirectory("emptyDir");
        File emptyFolder = tempDir.toFile();

        Set<String> cacheSet = GradleCacheExplorer.buildCacheSet(emptyFolder);
        Assertions.assertTrue(cacheSet.isEmpty(), "Cache set should be empty");

        Files.delete(tempDir);
    }

    @Test
    public void build_cache_returns_full_cache() throws IOException {
        Path rootDir = Files.createTempDirectory("rootDir");
        Path groupDir = Files.createDirectory(rootDir.resolve("groupDir"));
        Files.createDirectory(groupDir.resolve("nameDir"));

        File rootFolder = rootDir.toFile();
        Set<String> cacheSet = GradleCacheExplorer.buildCacheSet(rootFolder);
        System.out.println(cacheSet);
        Assertions.assertTrue(cacheSet.contains("groupDir:nameDir"), "Cache set should contain 'groupDir:nameDir'");
    }

    @Test
    public void search_gets_all_results() {
        Set<String> inputSet =
                Set.of("com.example.library:subDir1", "com.example.library:subDir2", "com.different.library:subDir1");
        Set<String> resultSet = GradleCacheExplorer.searchCacheSet(inputSet, "com.example.library");

        Assertions.assertTrue(resultSet.contains("subDir1"), "Result set should contain 'subDir1'");
        Assertions.assertTrue(resultSet.contains("subDir2"), "Result set should contain 'subDir2'");
    }

    @Test
    public void empty_pattern_returns_all_inputs() {
        Set<String> inputSet = Set.of("com.example.library:subDir1", "com.example.library:subDir2");
        Set<String> resultSet = GradleCacheExplorer.searchCacheSet(inputSet, "");

        Assertions.assertEquals(
                inputSet, resultSet, "Result set should be the same as input set when pattern is empty");
    }

    @Test
    public void testGetCompletions() {
        DependencyGroup group = DependencyGroup.fromString("com.palantir.baseline");
        Set<String> completions = explorer.getCompletions(group);
        System.out.println(completions);
        Assertions.assertTrue(completions.contains("gradle-baseline-java-config"), "Completions should contain some examples");
    }
}
