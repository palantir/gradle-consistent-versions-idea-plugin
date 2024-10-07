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

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.immutables.value.Value;

public final class RepositoryLoader {
    private static final ObjectMapper XML_MAPPER = new XmlMapper().registerModule(new GuavaModule());
    private static final String MAVEN_REPOSITORIES_FILE_NAME = ".idea/gcv-maven-repositories.xml";
    private static final String DEFAULT = "https://repo.maven.apache.org/maven2/";

    private RepositoryLoader() {
        // Utility class; prevent instantiation
    }

    public static List<String> loadRepositories(Project project) {
        List<String> urls = new ArrayList<>();
        File mavenRepoFile = new File(project.getBasePath(), MAVEN_REPOSITORIES_FILE_NAME);

        if (!mavenRepoFile.exists()) {
            // Add maven central as a default so if they don't have a gcv-maven-repositories.xml yet they still get
            // completion
            urls.add(DEFAULT);
            return urls;
        }

        try {
            Repositories repositories = XML_MAPPER.readValue(mavenRepoFile, Repositories.class);
            for (RepositoryConfig config : repositories.repositories()) {
                urls.add(config.url());
            }
            return urls;
        } catch (IOException e) {
            return urls;
        }
    }

    @Value.Immutable
    @JsonDeserialize(as = ImmutableRepositoryConfig.class)
    @JsonSerialize(as = ImmutableRepositoryConfig.class)
    interface RepositoryConfig {

        @Value.Parameter
        @JacksonXmlProperty(isAttribute = true)
        String url();
    }

    @Value.Immutable
    @JsonDeserialize(as = ImmutableRepositories.class)
    @JsonSerialize(as = ImmutableRepositories.class)
    @JsonRootName("repositories")
    interface Repositories {

        @Value.Parameter
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "repository")
        List<RepositoryConfig> repositories();
    }
}
