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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.palantir.gradle.versions.intellij.ContentsUtil.ContentResults;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryExplorer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryExplorer.class);

    private static final Pattern UNSTABLE_VERSION_PATTERN = Pattern.compile(
            ".*(-rc(-?\\d+)?|-SNAPSHOT|-M\\d+|-alpha(-?\\d+)?|-beta(-?\\d+)?)$", Pattern.CASE_INSENSITIVE);

    private final Cache<String, Set<GroupPartOrPackageName>> folderCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    // In general, we don't want to be caching version data as it changes often. However, for wildcard complete it
    // can be very expensive to repeatedly get data that realistically doesn't change on a second by second basis so
    // having a short-lived cache is okay
    private final Cache<String, Set<DependencyVersion>> shortLivedVersionCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    public final Set<GroupPartOrPackageName> getGroupPartOrPackageName(DependencyGroup group, String url) {
        String urlString = url + group.asUrlString();

        Set<GroupPartOrPackageName> cachedGroupPartOrPackageName = folderCache.getIfPresent(urlString);
        if (cachedGroupPartOrPackageName != null) {
            return cachedGroupPartOrPackageName;
        }

        ContentResults result = fetchContent(urlString);

        if (result.isEmpty()) {
            log.debug("Fetch cancelled or failed");
            return Collections.emptySet();
        }

        if (result.isError()) {
            log.debug("Content fetch failed with a {} response code", result.responseCode());
            if (result.responseCode() >= 400 && result.responseCode() < 500) {
                folderCache.put(urlString, Collections.emptySet());
            }
            return Collections.emptySet();
        }

        Set<GroupPartOrPackageName> parsedGroupPartOrPackageName = fetchFoldersFromContent(result.content());
        folderCache.put(urlString, parsedGroupPartOrPackageName);
        return parsedGroupPartOrPackageName;
    }

    public final VersionResults getVersions(DependencyGroup group, DependencyName dependencyPackage, String url) {
        String urlString = url + group.asUrlString() + dependencyPackage.name() + "/maven-metadata.xml";

        Set<DependencyVersion> cacheVersions = shortLivedVersionCache.getIfPresent(urlString);
        if (cacheVersions != null) {
            return VersionResults.of(cacheVersions, false);
        }

        ContentResults result = fetchContent(urlString);

        if (result.isEmpty()) {
            log.debug("Fetch of metadata cancelled or failed");
            return VersionResults.empty(false);
        }

        if (result.isError()) {
            log.debug("Metadata fetch failed with a {} response code", result.responseCode());
            if (result.responseCode() >= 400 && result.responseCode() < 500) {
                folderCache.put(urlString, Collections.emptySet());
            }
            return VersionResults.empty(false);
        }

        Set<DependencyVersion> parsedVersions = parseVersionsFromContent(result.content());
        shortLivedVersionCache.put(urlString, parsedVersions);

        // Only want to trigger a refresh if something has been added
        return VersionResults.of(parsedVersions, true);
    }

    private ContentResults fetchContent(String urlString) {
        try {
            URL url = new URL(urlString);
            return ContentsUtil.fetchPageContents(url);
        } catch (MalformedURLException e) {
            log.error("Malformed URL", e);
            return ContentResults.empty();
        }
    }

    private Set<GroupPartOrPackageName> fetchFoldersFromContent(String contents) {
        Set<GroupPartOrPackageName> folders = new HashSet<>();

        Document doc = Jsoup.parse(contents);
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            if (href.endsWith("/") && !href.contains(".")) {
                folders.add(GroupPartOrPackageName.of(href.substring(0, href.length() - 1)));
            }
        }
        return folders;
    }

    private Set<DependencyVersion> parseVersionsFromContent(String content) {
        try {
            XmlMapper xmlMapper = new XmlMapper();

            Metadata metadata = xmlMapper.readValue(content, Metadata.class);
            return parseVersionsFromContent(metadata);
        } catch (Exception e) {
            log.error("Failed to parse maven-metadata.xml", e);
        }
        return Collections.emptySet();
    }

    @VisibleForTesting
    final Set<DependencyVersion> parseVersionsFromContent(Metadata metadata) {
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
                .filter(this::isStableVersion)
                .or(() -> Lists.reverse(allVersions).stream()
                        .filter(this::isStableVersion)
                        .findFirst())
                .orElse(releaseOrLatestVersion);

        return allVersions.stream()
                .map(version -> DependencyVersion.of(version, latestStableVersion.equals(version)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isStableVersion(String version) {
        return !UNSTABLE_VERSION_PATTERN
                .matcher(version.toLowerCase(Locale.ROOT))
                .matches();
    }

    @Value.Immutable
    public abstract static class VersionResults {
        protected abstract Set<DependencyVersion> versions();

        protected abstract Boolean contentAdded();

        public static VersionResults of(Set<DependencyVersion> versions, Boolean contentAdded) {
            return ImmutableVersionResults.builder()
                    .versions(versions)
                    .contentAdded(contentAdded)
                    .build();
        }

        public static VersionResults empty(Boolean contentAdded) {
            return ImmutableVersionResults.builder()
                    .versions(Collections.emptySet())
                    .contentAdded(contentAdded)
                    .build();
        }
    }
}
