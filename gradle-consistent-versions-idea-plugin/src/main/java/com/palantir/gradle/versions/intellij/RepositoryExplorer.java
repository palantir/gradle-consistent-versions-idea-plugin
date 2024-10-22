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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.immutables.value.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryExplorer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryExplorer.class);

    private final Cache<String, Set<GroupPartOrPackageName>> folderCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    // In general, we don't want to be caching version data as it changes regurlly. However, for wildcard complete it
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

        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Page does not exist");
            return Collections.emptySet();
        }

        Set<GroupPartOrPackageName> parsedGroupPartOrPackageName = fetchFoldersFromContent(content.get());

        folderCache.put(urlString, parsedGroupPartOrPackageName);
        return parsedGroupPartOrPackageName;
    }

    public final Set<DependencyVersion> getVersions(
            DependencyGroup group, DependencyName dependencyPackage, String url) {
        String urlString = url + group.asUrlString() + dependencyPackage.name() + "/maven-metadata.xml";

        Set<DependencyVersion> cacheVersions = shortLivedVersionCache.getIfPresent(urlString);
        if (cacheVersions != null) {
            return cacheVersions;
        }

        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Empty metadata content received");
            return Collections.emptySet();
        }

        Set<DependencyVersion> parsedVersions = parseVersionsFromContent(content.get());
        shortLivedVersionCache.put(urlString, parsedVersions);
        return parsedVersions;
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
        Set<DependencyVersion> versions = new LinkedHashSet<>();
        try {
            XmlMapper xmlMapper = new XmlMapper();

            Metadata metadata = xmlMapper.readValue(content, Metadata.class);
            if (metadata.versioning() != null && metadata.versioning().versions() != null) {
                String latest = metadata.versioning().latest();
                for (String version : metadata.versioning().versions()) {
                    versions.add(DependencyVersion.of(version, latest.equals(version)));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse maven-metadata.xml", e);
        }
        return versions;
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
