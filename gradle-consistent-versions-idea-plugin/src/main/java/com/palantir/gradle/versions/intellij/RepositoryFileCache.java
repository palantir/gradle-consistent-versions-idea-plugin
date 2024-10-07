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

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryFileCache {
    private static final Logger log = LoggerFactory.getLogger(RepositoryFileCache.class);

    private static final String CACHE_PATH = System.getProperty("user.home") + "/.gcv-cache";

    private static final Map<String, Set<String>> cache = new HashMap<>();

    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    public final void syncCache(String repoUrl, Set<String> packages) {
        CompletableFuture<Void> unused = CompletableFuture.runAsync(
                () -> {
                    Set<String> existingPackages = cache.get(repoUrl);

                    if (existingPackages == null) {
                        existingPackages = loadCacheFromFile(repoUrl);
                        if (existingPackages != null) {
                            cache.put(repoUrl, existingPackages);
                        } else {
                            existingPackages = new HashSet<>();
                        }
                    }

                    Set<String> diffPackages = new HashSet<>(packages);
                    diffPackages.removeAll(existingPackages);

                    // Process new packages asynchronously and update cache immediately
                    Set<String> finalExistingPackages = existingPackages;
                    diffPackages.forEach(pkg -> {
                        CompletableFuture<Void> diffUnused = CompletableFuture.supplyAsync(
                                        () -> modifyPackage(repoUrl, pkg), executorService)
                                .thenAccept(newPackage -> {
                                    synchronized (finalExistingPackages) {
                                        if (finalExistingPackages.add(newPackage)) {
                                            cache.put(repoUrl, finalExistingPackages);
                                            writeCacheToFile(repoUrl, finalExistingPackages);
                                        }
                                    }
                                });
                    });
                },
                executorService);
    }

    public final Set<String> suggestions(String repoUrl, DependencyGroup group) {
        Set<String> allSuggestions = cache.get(repoUrl);
        String groupString = String.join(".", group.parts());

        if (allSuggestions == null) {
            allSuggestions = loadCacheFromFile(repoUrl);
            if (allSuggestions != null) {
                cache.put(repoUrl, allSuggestions);
            } else {
                return new HashSet<>();
            }
        }

        return allSuggestions.stream()
                .filter(suggestion -> suggestion.startsWith(groupString))
                .map(suggestion -> {
                    String result = suggestion.substring(groupString.length());
                    if (result.startsWith(".") || result.startsWith(":")) {
                        return result.substring(1);
                    }
                    return result;
                })
                .collect(Collectors.toSet());
    }

    private String modifyPackage(String repoUrl, String packageName) {
        String urlString = repoUrl + packageName.replaceAll("\\.", "/") + "/maven-metadata.xml";
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return packageName.replaceAll("\\.(?=[^.]*$)", ":");
            }
        } catch (IOException e) {
            log.error("Failed to get maven-metadata", e);
        }
        return packageName;
    }

    private Set<String> loadCacheFromFile(String repoUrl) {
        try {
            String hashedFileName = hashRepoUrl(repoUrl);
            Path cacheFilePath = Paths.get(CACHE_PATH, hashedFileName);

            if (Files.exists(cacheFilePath)) {
                return new HashSet<>(Files.readAllLines(cacheFilePath));
            }
        } catch (IOException e) {
            log.error("Failed to load cache from file", e);
        }
        return null;
    }

    private void writeCacheToFile(String repoUrl, Set<String> packages) {
        try {
            String hashedFileName = hashRepoUrl(repoUrl);
            Path cacheFilePath = Paths.get(CACHE_PATH, hashedFileName);

            if (!Files.exists(cacheFilePath.getParent())) {
                Files.createDirectories(cacheFilePath.getParent());
            }

            Files.write(cacheFilePath, packages, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            log.error("Failed to write cache to file", e);
        }
    }

    private String hashRepoUrl(String repoUrl) {
        return Hashing.sha256().hashString(repoUrl, StandardCharsets.UTF_8).toString();
    }
}
