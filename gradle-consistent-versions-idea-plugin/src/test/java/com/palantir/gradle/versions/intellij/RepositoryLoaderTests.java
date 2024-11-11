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

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class RepositoryLoaderTests extends LightJavaCodeInsightFixtureTestCase5 {

    private static final String MAVEN_REPOSITORIES_FILE_NAME = ".idea/gcv-maven-repositories.xml";
    private static final String DEFAULT = "https://repo.maven.apache.org/maven2/";

    @Override
    protected final String getRelativePath() {
        return "";
    }

    @Override
    protected final String getTestDataPath() {
        return "";
    }

    @Test
    void file_does_not_exist_return_default() {
        Project project = getFixture().getProject();

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertThat(repositories)
                .as("Should return default repository when file does not exist")
                .containsExactly(DEFAULT);
    }

    @Test
    void loads_repos_for_file() throws IOException {
        Project project = getFixture().getProject();

        createMavenRepositoriesFile(
                project,
                """
                    <repositories>
                      <repository url="https://repo1.maven.org/maven2/"/>
                      <repository url="https://repo2.maven.org/maven2/"/>
                    </repositories>
                    """);

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertThat(repositories)
                .as("Should load repositories from file")
                .containsExactlyInAnyOrder("https://repo1.maven.org/maven2/", "https://repo2.maven.org/maven2/");
    }

    @Test
    void local_host_and_file_paths_ignored() throws IOException {
        Project project = getFixture().getProject();

        createMavenRepositoriesFile(
                project,
                """
                    <repositories>
                      <repository url="https://repo1.maven.org/maven2/"/>
                      <repository url="http://localhost:8081/nexus/content/repositories/releases/"/>
                      <repository url="file:///Users/user/.m2/repository/"/>
                    </repositories>
                    """);

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertThat(repositories)
                .as("Should ignore localhost and file repositories")
                .containsExactly("https://repo1.maven.org/maven2/");
    }

    @Test
    void check_order_is_corrected() throws IOException {
        Project project = getFixture().getProject();

        createMavenRepositoriesFile(
                project,
                """
                    <repositories>
                      <repository url="dist"/>
                      <repository url="internal"/>
                      <repository url="RELEASE-dist"/>
                      <repository url="internal-dist"/>
                      <repository url="internal-jar"/>
                      <repository url="release-JAR"/>
                      <repository url="release"/>
                      <repository url="random"/>
                      <repository url="jar"/>
                    </repositories>
                    """);

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertThat(repositories)
                .as("Should maintain the correct order of repositories")
                .containsExactly(
                        "release-jar",
                        "release",
                        "release-dist",
                        "jar",
                        "internal-jar",
                        "random",
                        "dist",
                        "internal",
                        "internal-dist");
    }

    @Test
    void repos_not_sorted_maintain_entry_order() throws IOException {
        Project project = getFixture().getProject();

        createMavenRepositoriesFile(
                project,
                """
                    <repositories>
                      <repository url="test1/internal"/>
                      <repository url="test1/release"/>
                      <repository url="random1"/>
                      <repository url="test2/internal"/>
                      <repository url="random2"/>
                      <repository url="test2/release"/>
                    </repositories>
                    """);

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertThat(repositories)
                .as("Should maintain the correct order of repositories")
                .containsExactly(
                        "test1/release", "test2/release", "random1", "random2", "test1/internal", "test2/internal");
    }

    private void createMavenRepositoriesFile(Project project, String content) throws IOException {
        Path path = Path.of(project.getBasePath(), MAVEN_REPOSITORIES_FILE_NAME);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
