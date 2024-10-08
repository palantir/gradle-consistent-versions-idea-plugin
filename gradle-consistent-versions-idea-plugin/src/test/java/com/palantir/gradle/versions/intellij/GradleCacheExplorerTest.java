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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleCacheExplorerTest {

    private GradleCacheExplorer explorer;

    @BeforeEach
    void beforeEach() {
        List<String> projectUrls = List.of("https://repo.maven.apache.org/maven2/", "https://jcenter.bintray.com/");
        explorer = new GradleCacheExplorer(projectUrls);
    }

    @Test
    void test_gets_valid_urls_only() {
        assertThat(explorer.isValidResourceUrl(
                        "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.pom"))
                .as("because the URL is from a known valid repository and ends with .pom")
                .isTrue();

        assertThat(explorer.isValidResourceUrl("https://jcenter.bintray.com/com/example/artifact/1.0/artifact-1.0.jar"))
                .as("because the URL is from a known valid repository and ends with .jar")
                .isTrue();

        assertThat(explorer.isValidResourceUrl("https://example.com/com/example/artifact/1.0/artifact-1.0.pom"))
                .as("because the URL is not from a known valid repository")
                .isFalse();

        assertThat(explorer.isValidResourceUrl(
                        "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.txt"))
                .as("because the URL ends with an invalid extension")
                .isFalse();
    }

    @Test
    void test_gets_all_strings_from_bin(@TempDir File tempDir) throws IOException {
        File tempFile = new File(tempDir, "test.bin");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8))) {
            writer.write("hello.jar\nworld.pom\nanother.jar\b\f");
        }

        Stream<String> result = explorer.extractStringsFromBinFile(tempFile.toPath());
        List<String> resultList = result.collect(Collectors.toList());

        assertThat(resultList)
                .as("because the file contains these specific strings")
                .containsOnly("hello.jar", "world.pom", "another.jar");
    }

    @Test
    void test_extract_group_artifact_from_url_correctly() {
        assertThat(explorer.extractGroupAndArtifactFromUrl(
                                "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.pom")
                        .get())
                .as("because the URL should be parsed into group and artifact")
                .isEqualTo("com.example:artifact");

        assertThat(explorer.extractGroupAndArtifactFromUrl(
                                "https://jcenter.bintray.com/com/example/artifact/1.0/artifact-1.0.jar")
                        .get())
                .as("because the URL should be parsed into group and artifact")
                .isEqualTo("com.example:artifact");
        assertThat(explorer.extractGroupAndArtifactFromUrl("https://jcenter.bintray.com/garbage"))
                .as("because the URL should be parsed into group and artifact")
                .isEmpty();
    }
}
