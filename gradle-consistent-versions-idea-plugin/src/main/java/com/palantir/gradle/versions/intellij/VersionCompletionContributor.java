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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.palantir.gradle.versions.intellij.psi.VersionPropsDependencyVersion;
import com.palantir.gradle.versions.intellij.psi.VersionPropsProperty;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import one.util.streamex.StreamEx;

public class VersionCompletionContributor extends CompletionContributor {

    private static final RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

    private final Cache<String, Set<String>> repoCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    VersionCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(VersionPropsTypes.VERSION),
                new CompletionProvider<>() {
                    @Override
                    public void addCompletions(
                            CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                        VersionPropsDependencyVersion versionElement =
                                ReadAction.compute(() -> (VersionPropsDependencyVersion)
                                        parameters.getPosition().getParent());

                        VersionPropsProperty property = ReadAction.compute(() -> findParentProperty(versionElement));

                        DependencyGroup group = DependencyGroup.fromString(
                                property.getDependencyGroup().getText());
                        DependencyName dependencyName =
                                DependencyName.of(property.getDependencyName().getText());

                        Project project = parameters.getOriginalFile().getProject();

                        if (!dependencyName.name().contains("*")) {
                            addToResults(resultSet, RepositoryLoader.loadRepositories(project), group, dependencyName);
                            return;
                        }
                        Set<String> relevantRepos = findRelevantRepos(project, group);
                        Set<DependencyName> allPossibleNames = StreamEx.of(relevantRepos)
                                .flatMap(url -> repositoryExplorer.getGroupPartOrPackageName(group, url).stream())
                                .map(pkgName -> DependencyName.of(pkgName.name()))
                                .filter(pkgName -> pkgName.name()
                                        .startsWith(dependencyName.name().replace("*", "")))
                                .collect(Collectors.toSet());

                        allPossibleNames.forEach(pkgName -> addToResults(resultSet, relevantRepos, group, pkgName));
                    }

                    private Set<String> findRelevantRepos(Project project, DependencyGroup group) {
                        Set<String> cachedRelevantRepos = repoCache.getIfPresent(group.asUrlString());
                        if (cachedRelevantRepos != null && !cachedRelevantRepos.isEmpty()) {
                            return cachedRelevantRepos;
                        }

                        Set<String> relevantRepos = RepositoryLoader.loadRepositories(project).stream()
                                .filter(url -> {
                                    Set<GroupPartOrPackageName> folders =
                                            repositoryExplorer.getGroupPartOrPackageName(group, url);
                                    return !folders.isEmpty();
                                })
                                .collect(Collectors.toSet());

                        repoCache.put(group.asUrlString(), relevantRepos);
                        return relevantRepos;
                    }

                    private void addToResults(
                            CompletionResultSet resultSet,
                            Set<String> repos,
                            DependencyGroup group,
                            DependencyName dependencyName) {

                        StreamEx.of(repos)
                                .flatMap(url -> repositoryExplorer.getVersions(group, dependencyName, url).stream())
                                .zipWith(IntStream.iterate(0, i -> i + 1).boxed())
                                .mapKeyValue(this::getLookupElement)
                                .forEach(resultSet::addElement);
                    }

                    private LookupElement getLookupElement(DependencyVersion version, Integer priority) {
                        return version.isLatest()
                                ? PrioritizedLookupElement.withPriority(
                                        LookupElementBuilder.create(version)
                                                .withTypeText("Latest", true)
                                                .withLookupString("latest"),
                                        Double.MAX_VALUE)
                                : PrioritizedLookupElement.withPriority(LookupElementBuilder.create(version), priority);
                    }
                });
    }

    private VersionPropsProperty findParentProperty(VersionPropsDependencyVersion versionElement) {
        return versionElement == null ? null : PsiTreeUtil.getParentOfType(versionElement, VersionPropsProperty.class);
    }

    @Override
    public final boolean invokeAutoPopup(PsiElement position, char typeChar) {
        return true;
    }
}
