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
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryExplorer {
    private static final Logger log = LoggerFactory.getLogger(RepositoryExplorer.class);

    private final String baseUrl;

    public RepositoryExplorer(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public final List<Folder> getFolders(DependencyGroup group) {
        String urlString = baseUrl + group.asUrlString();
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Page does not exist");
            return new ArrayList<>();
        }

        return fetchFoldersFromContent(content.get());
    }

    public final List<Folder> getFoldersFromGradleCache(DependencyGroup group) {
        String gradleCachePath = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1";
        Map<Folder, Object> tree = new HashMap<>();
        buildGradleCacheTreeFromFolder(new File(gradleCachePath), tree);
        return searchGradleCacheTree(tree, group.parts());
    }

    public final List<DependencyVersion> getVersions(DependencyGroup group, DependencyName dependencyPackage) {
        String urlString = baseUrl + group.asUrlString() + dependencyPackage.name() + "/maven-metadata.xml";
        Optional<String> content = fetchContent(urlString);

        if (content.isEmpty()) {
            log.debug("Empty metadata content received");
            return new ArrayList<>();
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

    private List<Folder> fetchFoldersFromContent(String contents) {
        List<Folder> folders = new ArrayList<>();

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

    private List<DependencyVersion> parseVersionsFromContent(String content) {
        List<DependencyVersion> versions = new ArrayList<>();
        try {
            XmlMapper xmlMapper = new XmlMapper();

            Metadata metadata = xmlMapper.readValue(content, Metadata.class);
            if (metadata.versioning() != null && metadata.versioning().versions() != null) {
                for (String version : metadata.versioning().versions()) {
                    versions.add(DependencyVersion.of(version));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse maven-metadata.xml", e);
        }
        return versions;
    }

    public static void buildGradleCacheTreeFromFolder(File folder, Map<Folder, Object> tree) {
        if (folder.exists() && folder.isDirectory()) {
            for (File subFolder : Objects.requireNonNull(folder.listFiles())) {
                if (subFolder.isDirectory()) {
                    String folderName = subFolder.getName();
                    String[] parts = folderName.split("\\.");
                    Map<Folder, Object> currentLevel = tree;
                    for (String part : parts) {
                        currentLevel  = (Map<Folder, Object>) currentLevel.computeIfAbsent(Folder.of(part), k -> new HashMap<Folder, Object>());
                    }
                    for (File subSubFolder : Objects.requireNonNull(subFolder.listFiles())) {
                        if (subSubFolder.isDirectory()) {
                            String subFolderName = subSubFolder.getName();
                            currentLevel.put(Folder.of(subFolderName), new HashMap<Folder, Object>());
                        }
                    }
                }
            }
        }
    }

    public static List<Folder> searchGradleCacheTree(Map<Folder, Object> tree, List<String> parts) {
        Map<Folder, Object> currentLevel = tree;
        for (String part : parts) {
            if (currentLevel.containsKey(Folder.of(part))) {
                currentLevel = (Map<Folder, Object>) currentLevel.get(Folder.of(part));
            } else {
                return null;
            }
        }
        return new ArrayList<>(currentLevel.keySet());
    }
}
