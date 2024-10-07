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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryFileCache {
    private static final Logger log = LoggerFactory.getLogger(RepositoryFileCache.class);

    private static final String CACHE_PATH = System.getProperty("user.home") + "/.gcv-cache";

    private static final Map<String, Set<String>> cache = new HashMap<>();

    public final void syncCache(String repoUrl, Set<String> packages) {
        Set<String> existingPackages = cache.get(repoUrl);

        if (existingPackages == null) {
            existingPackages = loadCacheFromFile(repoUrl);
            if (existingPackages != null) {
                cache.put(repoUrl, existingPackages);
            } else {
                existingPackages = new HashSet<>();
            }
        }

        boolean hasChanges = existingPackages.addAll(packages);

        if (hasChanges) {
            cache.put(repoUrl, existingPackages);
            writeCacheToFile(repoUrl, existingPackages);
        }
    }

    private Set<String> loadCacheFromFile(String repoUrl) {
        try {
            String hashedFileName = hashRepoUrl(repoUrl);
            Path cacheFilePath = Paths.get(CACHE_PATH, hashedFileName);

            if (Files.exists(cacheFilePath)) {
                return new HashSet<>(Files.readAllLines(cacheFilePath));
            }
        } catch (IOException | NoSuchAlgorithmException e) {
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

        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to write cache to file", e);
        }
    }

    private String hashRepoUrl(String repoUrl) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(repoUrl.getBytes());
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
