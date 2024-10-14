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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleCacheExplorerTest {

    private GradleCacheExplorer explorer;

    @BeforeEach
    void beforeEach() {
        explorer = new GradleCacheExplorer();
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

        assertThat(explorer.isValidResourceUrl("example.com/com/example/artifact/1.0/artifact-1.0.pom"))
                .as("because the URL is not a valid URL")
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
        Set<String> projectUrls = Set.of("https://repo.maven.apache.org/maven2/", "https://jcenter.bintray.com/");

        assertThat(explorer.extractGroupAndArtifactFromUrl(
                                projectUrls,
                                "https://repo.maven.apache.org/maven2/com/example/artifact/1.0/artifact-1.0.pom")
                        .get())
                .as("because the URL should be parsed into group and artifact")
                .isEqualTo("com.example:artifact");

        assertThat(explorer.extractGroupAndArtifactFromUrl(
                                projectUrls, "https://jcenter.bintray.com/com/example/artifact/1.0/artifact-1.0.jar")
                        .get())
                .as("because the URL should be parsed into group and artifact")
                .isEqualTo("com.example:artifact");
        assertThat(explorer.extractGroupAndArtifactFromUrl(
                        projectUrls, "https://not.vaild.com/example/artifact/1.0/artifact-1.0.jar"))
                .as("Expected the URL to not match any project URL, resulting in an empty Optional")
                .isEmpty();
        assertThat(explorer.extractGroupAndArtifactFromUrl(projectUrls, "https://jcenter.bintray.com/com/example"))
                .as("Could not find second to last slash, resulting in an empty Optional")
                .isEmpty();
        assertThat(explorer.extractGroupAndArtifactFromUrl(projectUrls, ""))
                .as("Empty passed in so empty returned")
                .isEmpty();
    }

    @Test
    void test_get_completion_no_match() {
        Set<String> repoUrls = Set.of("https://example.one/", "https://example.two/");
        Set<String> cache = Set.of(
                "https://example.one/com/exampleOne/artifactOne/1.0/artifact-1.0.pom",
                "https://example.one/com/exampleOne/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/com/exampleDifferent/artifactOne/1.0/artifact-1.0.jar",
                "https://example.two/com/exampleTwo/artifactOne/1.0/artifact-1.0.pom",
                "https://example.two/com/exampleTwo/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/org/exampleOrg/artifactOrg/1.0/artifact-1.0.pom",
                "https://example.three/com/exampleThree/artifactOne/1.0/artifact-1.0.pom");

        explorer.setCacheForTesting(cache);
        assertThat(explorer.getCompletions(repoUrls, DependencyGroup.fromString("org.expected"), false))
                .as("No group match")
                .isEmpty();
    }

    @Test
    void test_get_completion_match_all() {
        Set<String> repoUrls = Set.of("https://example.one/", "https://example.two/");
        Set<String> cache = Set.of(
                "https://example.one/com/exampleOne/artifactOne/1.0/artifact-1.0.pom",
                "https://example.one/com/exampleOne/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/com/exampleDifferent/artifactOne/1.0/artifact-1.0.jar",
                "https://example.two/com/exampleTwo/artifactOne/1.0/artifact-1.0.pom",
                "https://example.two/com/exampleTwo/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/org/exampleOrg/artifactOrg/1.0/artifact-1.0.pom",
                "https://example.three/com/exampleThree/artifactOne/1.0/artifact-1.0.pom");

        explorer.setCacheForTesting(cache);
        assertThat(explorer.getCompletions(repoUrls, DependencyGroup.fromString(""), false))
                .as("Matches all")
                .containsOnly(
                        "com.exampleDifferent:artifactOne",
                        "com.exampleTwo:*",
                        "org.exampleOrg:artifactOrg",
                        "com.exampleOne:artifactOne",
                        "com.exampleTwo:artifactTwo",
                        "com.exampleOne:*",
                        "com.exampleOne:artifactTwo",
                        "com.exampleTwo:artifactOne");
    }

    @Test
    void test_get_completion_match_some() {
        Set<String> repoUrls = Set.of("https://example.one/", "https://example.two/");
        Set<String> cache = Set.of(
                "https://example.one/com/exampleOne/artifactOne/1.0/artifact-1.0.pom",
                "https://example.one/com/exampleOne/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/com/exampleDifferent/artifactOne/1.0/artifact-1.0.jar",
                "https://example.two/com/exampleTwo/artifactOne/1.0/artifact-1.0.pom",
                "https://example.two/com/exampleTwo/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/org/exampleOrg/artifactOrg/1.0/artifact-1.0.pom",
                "https://example.three/com/exampleThree/artifactOne/1.0/artifact-1.0.pom");

        explorer.setCacheForTesting(cache);
        assertThat(explorer.getCompletions(repoUrls, DependencyGroup.fromString("com"), false))
                .as("Matches all")
                .containsOnly(
                        "exampleOne:*",
                        "exampleOne:artifactOne",
                        "exampleOne:artifactTwo",
                        "exampleDifferent:artifactOne",
                        "exampleTwo:*",
                        "exampleTwo:artifactOne",
                        "exampleTwo:artifactTwo");
    }

    @Test
    void test_get_completion_match_is_package_name() {
        Set<String> repoUrls = Set.of("https://example.one/", "https://example.two/");
        Set<String> cache = Set.of(
                "https://example.one/com/exampleOne/artifactOne/1.0/artifact-1.0.pom",
                "https://example.one/com/exampleOne/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/com/exampleOne/anotherGroup/artifactOne/1.0/artifact-1.0.jar",
                "https://example.one/com/exampleDifferent/artifactOne/1.0/artifact-1.0.jar",
                "https://example.two/com/exampleTwo/artifactOne/1.0/artifact-1.0.pom",
                "https://example.two/com/exampleTwo/artifactTwo/1.0/artifact-1.0.jar",
                "https://example.one/org/exampleOrg/artifactOrg/1.0/artifact-1.0.pom",
                "https://example.three/com/exampleThree/artifactOne/1.0/artifact-1.0.pom");

        explorer.setCacheForTesting(cache);
        assertThat(explorer.getCompletions(repoUrls, DependencyGroup.fromString("com.exampleOne"), true))
                .as("Matches all")
                .containsOnly("*", "artifactOne", "artifactTwo");
    }
}
