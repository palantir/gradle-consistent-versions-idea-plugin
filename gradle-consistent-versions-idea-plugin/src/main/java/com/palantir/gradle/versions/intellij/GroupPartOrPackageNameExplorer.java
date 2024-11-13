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

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.application.ApplicationManager;
import com.palantir.gradle.versions.intellij.ContentsUtil.ContentResults;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupPartOrPackageNameExplorer {
    private static final Logger log = LoggerFactory.getLogger(GroupPartOrPackageNameExplorer.class);

    private final AsyncLoadingCache<String, Set<GroupPartOrPackageName>> groupPartOrPackageNameCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .maximumSize(100)
                    .buildAsync(this::fetchAndParseFromUrl);

    public final Set<GroupPartOrPackageName> getGroupPartOrPackageName(
            DependencyGroup group, String url, Runnable onLoadMore) {
        String urlString = url + group.asUrlString();

        Optional<Set<GroupPartOrPackageName>> cachedGroupParts =
                Optional.ofNullable(groupPartOrPackageNameCache.synchronous().getIfPresent(urlString));

        if (cachedGroupParts.isPresent()) {
            return cachedGroupParts.get();
        }
        groupPartOrPackageNameCache.get(urlString).thenAccept(result -> {
            onLoadMore.run();
        });
        return Collections.emptySet();
    }

    private Set<GroupPartOrPackageName> fetchAndParseFromUrl(String urlString) {
        ContentResults result = ContentsUtil.fetchPageContents(urlString);

        if (result.isEmpty()) {
            log.warn("Fetch of content cancelled or failed: {}", result.responseCode());
            return Set.of();
        }

        if (result.isError()) {
            log.warn("Content fetch failed with a {} response code", result.responseCode());
            if (result.responseCode() >= 400 && result.responseCode() < 500) {
                groupPartOrPackageNameCache.put(urlString, CompletableFuture.completedFuture(Collections.emptySet()));
            }
            return Set.of();
        }

        return parseGroupPartOrPackageNameFromContent(result.content());
    }

    private Set<GroupPartOrPackageName> parseGroupPartOrPackageNameFromContent(String contents) {
        Set<GroupPartOrPackageName> groupPartsOrPackageNames = new HashSet<>();

        Document doc = Jsoup.parse(contents);
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            if (href.endsWith("/") && !href.contains(".")) {
                groupPartsOrPackageNames.add(GroupPartOrPackageName.of(href.substring(0, href.length() - 1)));
            }
        }
        return groupPartsOrPackageNames;
    }

    static GroupPartOrPackageNameExplorer getInstance() {
        return ApplicationManager.getApplication().getService(GroupPartOrPackageNameExplorer.class);
    }

    private GroupPartOrPackageNameExplorer() {}
}
