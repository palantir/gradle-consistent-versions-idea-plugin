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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GradleCacheExplorerTest {

    private GradleCacheExplorer explorer;

    @BeforeEach
    void setUp() {
        List<String> projectUrls = List.of("https://repo.maven.apache.org/maven2/", "https://jcenter.bintray.com/");
        explorer = new GradleCacheExplorer(projectUrls);
    }

    @Test
    void test_gets_valid_urls_only() {
        assertTrue(explorer.isValidResourceUrl(
                "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.pom"));
        assertTrue(
                explorer.isValidResourceUrl("https://jcenter.bintray.com/com/example/artifact/1.0/artifact-1.0.jar"));
        assertFalse(explorer.isValidResourceUrl("https://example.com/com/example/artifact/1.0/artifact-1.0.pom"));
        assertFalse(explorer.isValidResourceUrl(
                "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.txt"));
    }

    @Test
    void test_gets_all_strings_from_bin() throws IOException {
        File tempFile = File.createTempFile("test", ".bin");
        tempFile.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8))) {
            writer.write("hello.jar\nworld.pom\nanother.jar\b\f");
        }

        Set<String> result = explorer.extractStringsFromBinFile(tempFile);
        Set<String> expected = new HashSet<>(List.of("hello.jar", "world.pom", "another.jar"));

        assertEquals(expected, result);
    }

    @Test
    void test_is_printable_char() {
        assertTrue(explorer.isPrintableChar('A'));
        assertTrue(explorer.isPrintableChar(' '));
        assertFalse(explorer.isPrintableChar('\n'));
        assertFalse(explorer.isPrintableChar((char) 127));
    }

    @Test
    void test_url_sanitise_correctly() {
        assertEquals(
                "com.example:artifact",
                explorer.sanitiseUrl("https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.pom"));
        assertEquals(
                "com.example:artifact",
                explorer.sanitiseUrl("https://jcenter.bintray.com/com/example/artifact/1.0/artifact-1.0.jar"));
    }
    }