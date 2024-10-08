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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
            return new HashSet<>();
        }

        try {
            Future<Set<String>> future =
                    ApplicationManager.getApplication().executeOnPooledThread(extractStrings(indicator));
            Set<String> results =
                    com.intellij.openapi.application.ex.ApplicationUtil.runWithCheckCanceled(future::get, indicator);

            return results.stream()
                    .filter(result -> result.startsWith(parsedInput))
                    .map(result -> parsedInput.isEmpty() ? result : result.substring(parsedInput.length() + 1))
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.debug("Operation was cancelled", e);
        } catch (Exception e) {
            log.warn("Failed to get completions", e);
        }
        return new HashSet<>();
    }

    private Callable<Set<String>> extractStrings(ProgressIndicator indicator) {
        return () -> cache.get("metadata", key -> {
            Set<String> stringsSet = new HashSet<>();
            File gradleCacheFolder = new File(GRADLE_CACHE_PATH);
            File[] metadataFolders = gradleCacheFolder.listFiles((dir, name) -> name.startsWith("metadata-"));

            if (metadataFolders != null) {
                for (File metadataFolder : metadataFolders) {
                    if (indicator.isCanceled()) {
                        throw new RuntimeException("Operation cancelled");
                    }
                    File binFile = new File(metadataFolder, "resource-at-url.bin");
                    if (binFile.exists()) {
                        extractStringsFromBinFile(binFile).stream()
                                .filter(this::isValidResourceUrl)
                                .map(this::sanitiseUrl)
                                .forEach(stringsSet::add);
                    }
                }
            }
            return stringsSet;
        });
    }

    final boolean isValidResourceUrl(String url) {
        return projectUrls.stream().anyMatch(url::startsWith) && (url.endsWith(".pom") || url.endsWith(".jar"));
    }

    final Set<String> extractStringsFromBinFile(File binFile) {
        Set<String> result = new HashSet<>();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(binFile))) {
            StringBuilder currentString = new StringBuilder();
            int byteValue;

            while ((byteValue = bis.read()) != -1) {
                char charValue = (char) byteValue;
                if (isPrintableChar(charValue)) {
                    currentString.append(charValue);
                } else {
                    if (currentString.length() >= 4) {
                        result.add(currentString.toString());
                    }
                    currentString.setLength(0);
                }
            }

            if (currentString.length() >= 4) {
                result.add(currentString.toString());
            }
        } catch (IOException e) {
            log.error("Failed to extract strings from bin file", e);
        }

        return result;
    }

    final boolean isPrintableChar(char ch) {
        return ch >= 32 && ch <= 126; // Printable ASCII range
    }

    final String sanitiseUrl(String url) {
        String finalUrl = url;
        for (String projectUrl : projectUrls) {
            if (finalUrl.startsWith(projectUrl)) {
                finalUrl = finalUrl.substring(projectUrl.length());
                break;
            }
        }

        int lastSlashIndex = finalUrl.lastIndexOf('/');
        int secondLastSlashIndex = finalUrl.lastIndexOf('/', lastSlashIndex - 1);

        if (secondLastSlashIndex != -1) {
            finalUrl = finalUrl.substring(0, secondLastSlashIndex).replace('/', '.');
            finalUrl = finalUrl.replaceFirst("\\.([^.]*)$", ":$1");
        }

        return finalUrl;
    }
}
