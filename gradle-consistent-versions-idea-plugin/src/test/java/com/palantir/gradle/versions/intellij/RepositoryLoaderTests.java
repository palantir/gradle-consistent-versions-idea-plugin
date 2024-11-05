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

import static org.gradle.internal.impldep.org.testng.Assert.assertEquals;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @TempDir
    Path tempDir;

    @Test
    void testLoadRepositories_fileDoesNotExist() {
        Project project = getFixture().getProject();

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        assertEquals(Set.of(DEFAULT), repositories, "Should return default repository when file does not exist");
    }

    @Test
    void testLoadRepositories_fileExists() throws IOException {
        Project project = getFixture().getProject();

        createMavenRepositoriesFile(
                tempDir.resolve(MAVEN_REPOSITORIES_FILE_NAME),
                """
                    <repositories>
                      <repository url="https://repo1.maven.org/maven2/"/>
                      <repository url="https://repo2.maven.org/maven2/"/>
                    </repositories>
                    """);

        Set<String> repositories = RepositoryLoader.loadRepositories(project);
        System.out.println(repositories);
        assertEquals(
                Set.of("https://repo1.maven.org/maven2/", "https://repo2.maven.org/maven2/"),
                repositories,
                "Should load repositories from file");
    }
    //
    //    @Test
    //    void testLoadRepositories_ignoreLocalhostAndFile() throws IOException {
    //        createMavenRepositoriesFile(
    //                tempDir.resolve(MAVEN_REPOSITORIES_FILE_NAME),
    //                """
    //                <repositories>
    //                  <repository url="https://repo1.maven.org/maven2/"/>
    //                  <repository url="http://localhost:8081/nexus/content/repositories/releases/"/>
    //                  <repository url="file:///Users/user/.m2/repository/"/>
    //                </repositories>
    //                """);
    //
    //        Set<String> repositories = RepositoryLoader.loadRepositories(project);
    //        assertEquals(
    //                Set.of("https://repo1.maven.org/maven2/"),
    //                repositories,
    //                "Should ignore localhost and file repositories");
    //    }
    //
    //    @Test
    //    void testLoadRepositories_handlesIOException() throws IOException {
    //        // Simulate IOException by creating a directory instead of a file
    //        Files.createDirectory(tempDir.resolve(MAVEN_REPOSITORIES_FILE_NAME));
    //
    //        Set<String> repositories = RepositoryLoader.loadRepositories(project);
    //        assertEquals(Set.of(DEFAULT_REPO), repositories, "Should return default repository when IOException
    // occurs");
    //    }
    //
    private void createMavenRepositoriesFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
