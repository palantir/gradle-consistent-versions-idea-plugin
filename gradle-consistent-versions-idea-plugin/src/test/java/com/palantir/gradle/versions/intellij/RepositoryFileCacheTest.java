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

import java.util.HashSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryFileCacheTest {

    private RepositoryFileCache cache;

    @BeforeEach
    public void beforeEach() {
        cache = new RepositoryFileCache();
    }

    @Test
    public void can_add_and_get_suggestions() throws InterruptedException {
        String repoUrl = "https://repo1.maven.org/maven2/";
        Set<String> packages = new HashSet<>();
        packages.add("com.palantir");
        packages.add("com.fasterxml");

        cache.syncCache(repoUrl, packages);

        // sleep as adding to the cache can take some time
        Thread.sleep(2000);

        Set<String> cachedPackages = cache.suggestions(repoUrl, DependencyGroup.fromString("com"));
        Assertions.assertThat(cachedPackages).contains("palantir");
        Assertions.assertThat(cachedPackages).contains("fasterxml");
    }

    @Test
    public void correctly_modify_full_packages() throws InterruptedException {
        String repoUrl = "https://repo1.maven.org/maven2/";
        Set<String> packages = new HashSet<>();
        packages.add("com.palantir.baseline.baseline-error-prone");

        cache.syncCache(repoUrl, packages);

        // sleep as adding to the cache can take some time
        Thread.sleep(2000);

        Set<String> cachedPackages = cache.suggestions(repoUrl, DependencyGroup.fromString("com"));
        Assertions.assertThat(cachedPackages).contains("palantir.baseline:baseline-error-prone");
    }
}
