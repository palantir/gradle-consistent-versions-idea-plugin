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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryExplorer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryExplorer.class);
    private static final RepositoryFileCache FILE_CACHE = new RepositoryFileCache();
    private final String baseUrl;
    private static final Cache<DependencyGroup, Set<Folder>> folderCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    public RepositoryExplorer(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public final Set<Folder> getFolders(DependencyGroup group) {
        Set<Folder> folders = folderCache.get(group, key -> {
            Set<Folder> loadedFolders = loadFolders(key);
            return loadedFolders.isEmpty() ? null : loadedFolders;
        });

        return folders != null ? folders : Collections.emptySet();
    }

    private Set<Folder> loadFolders(DependencyGroup group) {
        String urlString = baseUrl + group.asUrlString();
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Page does not exist");
            return new HashSet<>();
        }

        Set<Folder> folders = fetchFoldersFromUrl(content.get());
        String groupString = String.join(".", group.parts());
        if (!groupString.isEmpty()) {
            groupString = groupString + ".";
        }
        Set<String> packageParts = new HashSet<>();
        for (Folder folder : folders) {
            packageParts.add(groupString + folder.name());
        }
        FILE_CACHE.syncCache(baseUrl, packageParts);

        return folders;
    }

    public final Set<DependencyVersion> getVersions(DependencyGroup group, DependencyName dependencyPackage) {
        String urlString = baseUrl + group.asUrlString() + dependencyPackage.name() + "/maven-metadata.xml";
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Empty metadata content received");
            return new HashSet<>();
        }

        return parseVersionsFromMetadata(content.get());
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

    private Set<Folder> fetchFoldersFromUrl(String contents) {
        Set<Folder> folders = new HashSet<>();

        Document doc = Jsoup.parse(contents);
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            if (href.endsWith("/") && !href.contains(".")) {
                folders.add(Folder.of(href.substring(0, href.length() - 1)));
            }
        }
        return folders;
    }

    private Set<DependencyVersion> parseVersionsFromMetadata(String content) {
        Set<DependencyVersion> versions = new HashSet<>();
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
}
