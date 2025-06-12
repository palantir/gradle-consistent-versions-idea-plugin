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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Service.Level;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.palantir.gradle.versions.intellij.ContentsUtil.ContentResults;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service(Level.APP)
public final class GroupPartOrPackageNameExplorer {
    private static final Logger log = LoggerFactory.getLogger(GroupPartOrPackageNameExplorer.class);

    @SuppressWarnings("for-rollout:PreferJavaTimeOverload")
    private final LoadingCache<String, Set<GroupPartOrPackageName>> groupPartOrPackageNameCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build(this::fetchAndParseFromUrl);

    public Set<GroupPartOrPackageName> getCancelableGroupPartOrPackageName(DependencyGroup group, RepositoryUrl url) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

        if (indicator == null) {
            return Collections.emptySet();
        }

        try {
            Future<Set<GroupPartOrPackageName>> future = ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> getGroupPartOrPackageName(group, url));
            return ApplicationUtil.runWithCheckCanceled(future::get, indicator);
        } catch (ProcessCanceledException e) {
            log.debug("progress cancelled", e);
        } catch (Exception e) {
            log.debug("Failed to fetch contents", e);
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    public Set<GroupPartOrPackageName> getGroupPartOrPackageName(DependencyGroup group, RepositoryUrl url) {
        String urlString = url.url() + group.asUrlString();

        try {
            return groupPartOrPackageNameCache.get(urlString);
        } catch (Exception e) {
            log.debug("Failed to get group parts or package names for URL: {}", urlString, e);
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("for-rollout:Slf4jLogsafeArgs")
    private Set<GroupPartOrPackageName> fetchAndParseFromUrl(String urlString) {
        ContentResults result = ContentsUtil.fetchPageContents(urlString);

        if (result.isEmpty()) {
            log.debug("Fetch of content cancelled or failed: {}", result.responseCode());
            return Collections.emptySet();
        }

        if (result.isError()) {
            log.debug("Content fetch failed with a {} response code", result.responseCode());
            return Collections.emptySet();
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
