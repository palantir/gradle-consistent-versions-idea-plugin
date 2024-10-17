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

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class RepositoryExplorerTests {

    @Test
    void test_gets_release_from_metadata() {
        RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

        Versioning versioning = Versioning.builder()
                .release("2.0.0")
                .latest("1.0.0")
                .versions(List.of("1.0.0", "2.0.0"))
                .lastUpdated("unimportant")
                .build();

        Metadata metadata = Metadata.builder()
                .groupId("com.example")
                .artifactId("example-artifact")
                .versioning(versioning)
                .build();

        Set<DependencyVersion> versions = repositoryExplorer.parseVersionsFromContent(metadata);

        assertThat(versions).as("Check that versions set is not empty").isNotEmpty();

        assertThat(versions)
                .as("Check that versions set contains the release version marked as latest")
                .anyMatch(v -> v.version().equals("2.0.0") && v.isLatest());
    }

    @Test
    void test_gets_latest_from_metadata_if_release_missing() {
        RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

        Versioning versioning = Versioning.builder()
                .release("")
                .latest("2.0.0")
                .versions(List.of("1.0.0", "2.0.0"))
                .lastUpdated("unimportant")
                .build();

        Metadata metadata = Metadata.builder()
                .groupId("com.example")
                .artifactId("example-artifact")
                .versioning(versioning)
                .build();

        Set<DependencyVersion> versions = repositoryExplorer.parseVersionsFromContent(metadata);

        assertThat(versions).as("Check that versions set is not empty").isNotEmpty();

        assertThat(versions)
                .as("Check that versions set contains the latest version marked as latest")
                .anyMatch(v -> v.version().equals("2.0.0") && v.isLatest());
    }

    @Test
    void test_unstable_versions_are_skipped() {
        RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

        Versioning versioning = Versioning.builder()
                .release("2.1.0-SNAPSHOT")
                .latest("2.1.0-SNAPSHOT")
                .versions(List.of(
                        "1.0.0",
                        "2.0.0",
                        "2.1.0-rc",
                        "2.1.0-rc1",
                        "2.1.0-rc-2",
                        "2.1.0-M2",
                        "2.1.0-alpha",
                        "2.1.0-alpha2",
                        "2.1.0-alpha-3",
                        "2.1.0-beta",
                        "2.1.0-beta-2",
                        "2.1.0-beta3",
                        "2.1.0-SNAPSHOT"))
                .lastUpdated("unimportant")
                .build();

        Metadata metadata = Metadata.builder()
                .groupId("com.example")
                .artifactId("example-artifact")
                .versioning(versioning)
                .build();

        Set<DependencyVersion> versions = repositoryExplorer.parseVersionsFromContent(metadata);

        assertThat(versions).as("Check that versions set is not empty").isNotEmpty();

        assertThat(versions)
                .as("Check that regex rejects all unstable versions")
                .anyMatch(v -> v.version().equals("2.0.0") && v.isLatest());
    }

    @Test
    void test_rc_in_name_is_matched() {
        RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

        Versioning versioning = Versioning.builder()
                .release("2.1.0-SNAPSHOT")
                .latest("2.1.0-SNAPSHOT")
                .versions(List.of("1.0.0", "rc-2.0.0", "2.1.0-SNAPSHOT"))
                .lastUpdated("unimportant")
                .build();

        Metadata metadata = Metadata.builder()
                .groupId("com.example")
                .artifactId("example-artifact")
                .versioning(versioning)
                .build();

        Set<DependencyVersion> versions = repositoryExplorer.parseVersionsFromContent(metadata);

        assertThat(versions).as("Check that versions set is not empty").isNotEmpty();

        assertThat(versions)
                .as("Check that rc can be used in a version name")
                .anyMatch(v -> v.version().equals("rc-2.0.0") && v.isLatest());
    }
}
