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
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

    private static final Pattern UNSTABLE_VERSION_PATTERN =
            Pattern.compile(".*(-rc\\d*|-SNAPSHOT|-M\\d+|-alpha|-beta)$", Pattern.CASE_INSENSITIVE);

    private final Cache<CacheKey, Set<GroupPartOrPackageName>> folderCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    public final Set<GroupPartOrPackageName> getGroupPartOrPackageName(DependencyGroup group, String url) {
        CacheKey cacheKey = CacheKey.of(url, group);
        Set<GroupPartOrPackageName> folders = folderCache.get(cacheKey, key -> {
            Set<GroupPartOrPackageName> loadedFolders = loadFolders(key.group(), url);
            return loadedFolders.isEmpty() ? null : loadedFolders;
        });

        return folders != null ? folders : Collections.emptySet();
    }

    private Set<GroupPartOrPackageName> loadFolders(DependencyGroup group, String url) {
        String urlString = url + group.asUrlString();
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Page does not exist");
            return Collections.emptySet();
        }

        return fetchFoldersFromContent(content.get());
    }

    public final Set<DependencyVersion> getVersions(
            DependencyGroup group, DependencyName dependencyPackage, String url) {
        String urlString = url + group.asUrlString() + dependencyPackage.name() + "/maven-metadata.xml";
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Empty metadata content received");
            return Collections.emptySet();
        }

        return parseVersionsFromContent(content.get());
    }

    private Optional<String> fetchContent(String urlString) {
        try {
            URL url = new URL(urlString);
            return ContentsUtil.fetchPageContents(url);
        } catch (MalformedURLException e) {
            log.error("Malformed URL", e);
            return Optional.empty();
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
            xmlMapper.registerModule(new GuavaModule());

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
        Collections.reverse(allVersions);

        if (allVersions.isEmpty()) {
            return Collections.emptySet();
        }

        String metadataReleaseOrLatest = Optional.ofNullable(
                        metadata.versioning().release())
                .filter(l -> !l.isEmpty())
                .orElseGet(() -> metadata.versioning().latest());

        String latestStableVersion = Optional.of(metadataReleaseOrLatest)
                .filter(this::isStableVersion)
                .or(() -> allVersions.stream().filter(this::isStableVersion).findFirst())
                .orElse(metadataReleaseOrLatest);

        return allVersions.stream()
                .map(version -> DependencyVersion.of(version, latestStableVersion.equals(version)))
                .collect(Collectors.toSet());
    }

    private boolean isStableVersion(String version) {
        return !UNSTABLE_VERSION_PATTERN
                .matcher(version.toLowerCase(Locale.ROOT))
                .matches();
    }

    @Value.Immutable
    interface CacheKey {
        String url();

        DependencyGroup group();

        static CacheKey of(String url, DependencyGroup group) {
            return ImmutableCacheKey.builder().url(url).group(group).build();
        }
    }
}
