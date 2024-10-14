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

import com.google.common.base.Stopwatch;
import com.intellij.openapi.application.ApplicationManager;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradleCacheExplorer {

    private static final Logger log = LoggerFactory.getLogger(GradleCacheExplorer.class);
    private static final String GRADLE_CACHE_PATH = System.getProperty("user.home") + "/.gradle/caches/modules-2/";
    private final AtomicReference<Set<String>> cache = new AtomicReference<>(Collections.emptySet());

    GradleCacheExplorer() {
        loadCache();
    }

    public final void loadCache() {
        cache.set(extractStrings());
    }

    public final Set<String> getCompletions(Set<String> repoUrls, DependencyGroup input, boolean isPackageName) {
        Stopwatch stopWatch = Stopwatch.createStarted();

        String parsedInput = String.join(".", input.parts());

        Set<String> results = cache.get().stream()
                .map(url -> extractGroupAndArtifactFromUrl(repoUrls, url))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        Set<String> resultsWithStarsIncluded = results.stream()
                .map(result -> {
                    int colonIndex = result.indexOf(':');
                    if (colonIndex != -1) {
                        return Arrays.asList(result, result.substring(0, colonIndex) + ":*");
                    }
                    return Collections.singletonList(result);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        if (parsedInput.isEmpty()) {
            return results;
        }

        Set<String> filteredResults = resultsWithStarsIncluded.stream()
                .filter(result -> result.startsWith(parsedInput))
                .map(result -> result.substring(parsedInput.length() + 1))
                .collect(Collectors.toSet());

        if (isPackageName) {
            filteredResults = filteredResults.stream()
                    .filter(result -> !result.contains(":"))
                    .collect(Collectors.toSet());
        }

        stopWatch.stop();
        log.debug("Completion matching time: {} ms", stopWatch.elapsed().toMillis());

        return filteredResults;
    }

    private Set<String> extractStrings() {
        try (Stream<Path> allFolders = Files.list(Paths.get(GRADLE_CACHE_PATH))) {

            Stream<Path> metadataFolders =
                    allFolders.filter(path -> path.getFileName().toString().startsWith("metadata-"));

            return metadataFolders
                    .map(metadataFolder -> metadataFolder.resolve("resource-at-url.bin"))
                    .filter(Files::exists)
                    .flatMap(this::extractStringsFromBinFile)
                    .filter(this::isValidResourceUrl)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Failed to list metadata folders", e);
            return Collections.emptySet();
        }
    }

    final boolean isValidResourceUrl(String url) {
        return (url.startsWith("https://")) && (url.endsWith(".pom") || url.endsWith(".jar"));
    }

    final Stream<String> extractStringsFromBinFile(Path binFile) {
        Set<String> result = new HashSet<>();
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(binFile))) {
            StringBuilder currentString = new StringBuilder();
            int byteValue;

            while ((byteValue = bis.read()) != -1) {
                char charValue = (char) byteValue;
                if (!Character.isISOControl(charValue)) {
                    currentString.append(charValue);
                    continue;
                }
                if (!currentString.isEmpty()) {
                    result.add(currentString.toString());
                    currentString.setLength(0);
                }
            }

            if (!currentString.isEmpty()) {
                result.add(currentString.toString());
            }
        } catch (IOException e) {
            log.error("Failed to extract strings from bin file", e);
        }
        return result.stream();
    }

    /**
     * Extracts the group and artifact identifiers from a given maven2 layout URL.
     *
     * <p>The method removes the base project URL from the input if it matches any in a predefined list,
     * then converts the remaining path to the format "group:artifact".
     *
     * <p>Example: For the URL "http://example.com/org/example/project/1.0/project-1.0.jar"
     * and "http://example.com/" in the list of projectUrls, it returns "org.example.project:project".
     *
     * @param url the URL to process
     * @return an {@link Optional} containing a string in the format "group:artifact" if extraction is successful,
     *         or {@link Optional#empty()} if no matching project URL is found or the URL does not have the expected structure.
     */
    Optional<String> extractGroupAndArtifactFromUrl(Set<String> repoUrls, String url) {
        return repoUrls.stream().filter(url::startsWith).findFirst().flatMap(projectUrl -> {
            String mavenLayout = url.substring(projectUrl.length());

            int lastSlashIndex = mavenLayout.lastIndexOf('/');
            if (lastSlashIndex == -1) {
                return Optional.empty();
            }

            int secondLastSlashIndex = mavenLayout.lastIndexOf('/', lastSlashIndex - 1);
            if (secondLastSlashIndex == -1) {
                return Optional.empty();
            }

            int thirdLastSlashIndex = mavenLayout.lastIndexOf('/', secondLastSlashIndex - 1);
            if (thirdLastSlashIndex == -1) {
                return Optional.empty();
            }

            String group = mavenLayout.substring(0, thirdLastSlashIndex).replace('/', '.');
            String artifact = mavenLayout.substring(thirdLastSlashIndex + 1, secondLastSlashIndex);

            return Optional.of(String.format("%s:%s", group, artifact));
        });
    }

    static GradleCacheExplorer getInstance() {
        return ApplicationManager.getApplication().getService(GradleCacheExplorer.class);
    }
}
