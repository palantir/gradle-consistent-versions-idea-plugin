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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Service.Level;
import com.palantir.gradle.versions.intellij.ContentsUtil.ContentResults;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service(Level.APP)
public final class VersionExplorer {
    private static final Logger log = LoggerFactory.getLogger(VersionExplorer.class);

    private static final Pattern UNSTABLE_VERSION_PATTERN = Pattern.compile(
            ".*(-rc(-?\\d+)?|-SNAPSHOT|-M\\d+|-alpha(-?\\d+)?|-beta(-?\\d+)?)$", Pattern.CASE_INSENSITIVE);

    // In general, we don't want to be caching version data as it changes often. However, for wildcard complete it
    // can be very expensive to repeatedly get data that realistically doesn't change on a second by second basis so
    // having a short-lived cache is okay
    private final AsyncLoadingCache<String, Set<DependencyVersion>> shortLivedVersionCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(10000)
            .buildAsync(this::fetchAndParseFromUrl);

    public record PackageInRepo(DependencyGroup group, DependencyName dependencyPackage, RepositoryUrl repositoryUrl) {}

    @SuppressWarnings("for-rollout:FutureReturnValueIgnored")
    public Set<DependencyVersion> getVersions(PackageInRepo groupAndDep, Runnable onLoadMore) {
        String urlString = urlFor(groupAndDep);

        Optional<Set<DependencyVersion>> cachedVersions =
                Optional.ofNullable(shortLivedVersionCache.synchronous().getIfPresent(urlString));

        if (cachedVersions.isPresent()) {
            return cachedVersions.get();
        }

        shortLivedVersionCache.get(urlString).thenAccept(result -> {
            onLoadMore.run();
        });

        return Collections.emptySet();
    }

    private Set<DependencyVersion> fetchAndParseFromUrl(String urlString) {
        ContentResults result = ContentsUtil.fetchPageContents(urlString);

        if (result.isEmpty()) {
            log.debug("Fetch of content cancelled or failed: {}", result.responseCode());
            return Set.of();
        }

        if (result.isError()) {
            log.debug("Content fetch failed with a {} response code", result.responseCode());
            return Set.of();
        }

        return parseVersionsFromContent(result.content());
    }

    private static @NotNull String urlFor(PackageInRepo groupAndDep) {
        return groupAndDep.repositoryUrl().url() + groupAndDep.group().asUrlString()
                + groupAndDep.dependencyPackage().name() + "/maven-metadata.xml";
    }

    private Set<DependencyVersion> parseVersionsFromContent(String content) {
        try {
            XmlMapper xmlMapper = new XmlMapper();

            Metadata metadata = xmlMapper.readValue(content, Metadata.class);
            return parseVersionsFromContent(metadata);
        } catch (Exception e) {
            log.error("Failed to parse maven-metadata.xml", e);
            log.error("Full content: \n", content);
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("for-rollout:MixedMutabilityReturnType")
    @VisibleForTesting
    static Set<DependencyVersion> parseVersionsFromContent(Metadata metadata) {
        List<String> allVersions = new ArrayList<>(metadata.versioning().versions());

        if (allVersions.isEmpty()) {
            return Collections.emptySet();
        }

        String releaseOrLatestVersion = Optional.ofNullable(
                        metadata.versioning().release())
                .filter(l -> !l.isEmpty())
                .orElseGet(() -> metadata.versioning().latest());

        // Check if the releaseOrLatestVersion is stable, it not find first stable version, if no stable versions return
        // the releaseOrLatestVersion
        String latestStableVersion = Optional.of(releaseOrLatestVersion)
                .filter(VersionExplorer::isStableVersion)
                .or(() -> Lists.reverse(allVersions).stream()
                        .filter(VersionExplorer::isStableVersion)
                        .findFirst())
                .orElse(releaseOrLatestVersion);

        return allVersions.stream()
                .map(version -> DependencyVersion.of(version, latestStableVersion.equals(version)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isStableVersion(String version) {
        return !UNSTABLE_VERSION_PATTERN
                .matcher(version.toLowerCase(Locale.ROOT))
                .matches();
    }

    static VersionExplorer getInstance() {
        return ApplicationManager.getApplication().getService(VersionExplorer.class);
    }

    private VersionExplorer() {}
}
