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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradleCacheExplorer {

    private static final Logger log = LoggerFactory.getLogger(GradleCacheExplorer.class);
    private static final String GRADLE_CACHE_PATH = System.getProperty("user.home") + "/.gradle/caches/modules-2/";
    private static final Cache<String, Set<String>> cache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    private final List<String> projectUrls;

    public GradleCacheExplorer(List<String> projectUrls) {
        this.projectUrls = projectUrls;
    }

    public final Set<String> getCompletions(DependencyGroup input) {
        String parsedInput = String.join(".", input.parts());
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
            return Collections.emptySet();
        }

        try {
            Callable<Set<String>> task = () -> extractStrings(indicator);
            Future<Set<String>> future = ApplicationManager.getApplication().executeOnPooledThread(task);
            Set<String> results =
                    com.intellij.openapi.application.ex.ApplicationUtil.runWithCheckCanceled(future::get, indicator);

            if (parsedInput.isEmpty()) {
                return results;
            }

            return results.stream()
                    .filter(result -> result.startsWith(parsedInput))
                    .map(result -> result.substring(parsedInput.length() + 1))
                    .collect(Collectors.toSet());
        } catch (CancellationException e) {
            log.debug("Operation was cancelled", e);
        } catch (Exception e) {
            log.warn("Failed to get completions", e);
        }
        return Collections.emptySet();
    }

    private Set<String> extractStrings(ProgressIndicator indicator) {
        return cache.get("metadata", key -> {
            try (Stream<Path> metadataFolders = Files.list(Paths.get(GRADLE_CACHE_PATH))
                    .filter(path -> path.getFileName().toString().startsWith("metadata-"))) {
                return metadataFolders
                        .filter(metadataFolder -> !indicator.isCanceled())
                        .map(metadataFolder -> metadataFolder.resolve("resource-at-url.bin"))
                        .filter(Files::exists)
                        .flatMap(this::extractStringsFromBinFile)
                        .filter(this::isValidResourceUrl)
                        .map(this::extractGroupAndArtifactFromUrl)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                log.error("Failed to list metadata folders", e);
                return Collections.emptySet();
            }
        });
    }

    final boolean isValidResourceUrl(String url) {
        return projectUrls.stream().anyMatch(url::startsWith) && (url.endsWith(".pom") || url.endsWith(".jar"));
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
     * Extracts the group and artifact identifiers from a given URL.
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
    public Optional<String> extractGroupAndArtifactFromUrl(String url) {
        Optional<String> optionalFinalUrl = projectUrls.stream()
                .filter(url::startsWith)
                .findFirst()
                .map(projectUrl -> url.substring(projectUrl.length()));

        if (optionalFinalUrl.isEmpty()) {
            return Optional.empty();
        }

        String finalUrl = optionalFinalUrl.get();

        int lastSlashIndex = finalUrl.lastIndexOf('/');
        int secondLastSlashIndex = finalUrl.lastIndexOf('/', lastSlashIndex - 1);

        if (secondLastSlashIndex == -1) {
            return Optional.empty();
        }

        finalUrl = finalUrl.substring(0, secondLastSlashIndex).replace('/', '.');
        finalUrl = finalUrl.replaceFirst("\\.([^.]*)$", ":$1");

        return Optional.of(finalUrl);
    }
}
