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

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
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
import com.palantir.gradle.versions.intellij.VersionExplorer.GroupAndDep;
import com.palantir.gradle.versions.intellij.psi.VersionPropsDependencyVersion;
import com.palantir.gradle.versions.intellij.psi.VersionPropsProperty;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import one.util.streamex.StreamEx;

public class VersionCompletionContributor extends CompletionContributor {
    private static final GroupPartOrPackageNameExplorer GROUP_PART_OR_PACKAGE_NAME_EXPLORER =
            new GroupPartOrPackageNameExplorer();
    private static final VersionExplorer VERSION_EXPLORER = new VersionExplorer();

    VersionCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(VersionPropsTypes.VERSION),
                new CompletionProvider<>() {
                    @Override
                    public void addCompletions(
                            CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {
                        DependencyInfo dependencyInfo = getDependencyInfo(parameters);
                        DependencyGroup group = dependencyInfo.group();
                        DependencyName dependencyName = dependencyInfo.dependencyName();

                        Project project = parameters.getOriginalFile().getProject();

                        CompletionSorter sorter = CompletionSorter.emptySorter().weigh(new VersionWeigher());
                        CompletionResultSet sortedResultSet = resultSet.withRelevanceSorter(sorter);

                        addLoadingElement(sortedResultSet);

                        if (!dependencyName.name().contains("*")) {
                            handleDependencyWithoutWildcard(sortedResultSet, project, group, dependencyName);
                            return;
                        }

                        List<GroupAndDep> groupAndDeps = collectGroupAndDeps(project, group, dependencyName);
                        addToResults(sortedResultSet, groupAndDeps);
                    }
                });
    }

    private DependencyInfo getDependencyInfo(CompletionParameters parameters) {
        VersionPropsDependencyVersion versionElement = ReadAction.compute(
                () -> (VersionPropsDependencyVersion) parameters.getPosition().getParent());

        VersionPropsProperty property = ReadAction.compute(() -> findParentProperty(versionElement));

        DependencyGroup group =
                DependencyGroup.fromString(property.getDependencyGroup().getText());
        DependencyName dependencyName =
                DependencyName.of(property.getDependencyName().getText());

        return new DependencyInfo(group, dependencyName);
    }

    private void addLoadingElement(CompletionResultSet sortedResultSet) {
        LookupElement loadingElement = PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create("Loading Versions...").withInsertHandler((elementContext, item) -> {
                    // Prevent insertion
                    elementContext
                            .getDocument()
                            .deleteString(elementContext.getStartOffset(), elementContext.getTailOffset());
                }),
                Double.MIN_VALUE);
        sortedResultSet.addElement(loadingElement);
    }

    private void handleDependencyWithoutWildcard(
            CompletionResultSet sortedResultSet,
            Project project,
            DependencyGroup group,
            DependencyName dependencyName) {
        RepositoryLoader.loadRepositories(project)
                .forEach(url -> addToResults(sortedResultSet, List.of(new GroupAndDep(group, dependencyName, url))));
    }

    private List<GroupAndDep> collectGroupAndDeps(
            Project project, DependencyGroup group, DependencyName dependencyName) {
        String dependencyNamePrefix = dependencyName.name().replace("*", "");
        return StreamEx.of(RepositoryLoader.loadRepositories(project))
                .flatMap(url -> StreamEx.of(GROUP_PART_OR_PACKAGE_NAME_EXPLORER.getGroupPartOrPackageName(
                                group, url, RefreshUtil.refreshOnceSupplier()::get))
                        .filter(pkgName -> pkgName.name().startsWith(dependencyNamePrefix))
                        .map(pkgName -> new SimpleEntry<>(url, pkgName)))
                .map(entry -> {
                    String url = entry.getKey();
                    GroupPartOrPackageName pkgName = entry.getValue();
                    DependencyName depName = DependencyName.of(pkgName.name());
                    return new GroupAndDep(group, depName, url);
                })
                .toList();
    }

    private void addToResults(CompletionResultSet resultSet, List<GroupAndDep> groupAndDeps) {
        Map<DependencyVersion, AtomicInteger> versionCounts = StreamEx.of(groupAndDeps)
                .flatMap(groupAndDep ->
                        VERSION_EXPLORER.getVersions(groupAndDep, RefreshUtil.refreshOnceSupplier()::get).stream())
                .collect(Collectors.toConcurrentMap(
                        Function.identity(), v -> new AtomicInteger(1), (existingCount, newCount) -> {
                            existingCount.addAndGet(newCount.get());
                            return existingCount;
                        }));

        List<LookupElement> lookupElements = versionCounts.entrySet().stream()
                .map(entry ->
                        createLookupElement(entry.getKey(), entry.getValue().get(), groupAndDeps.size()))
                .collect(Collectors.toList());

        resultSet.addAllElements(lookupElements);
    }

    private LookupElement createLookupElement(DependencyVersion version, int count, int total) {
        String typeText = (total > 1) ? count + "/" + total : "";
        if (version.isLatest()) {
            typeText += " (latest)";
            return LookupElementBuilder.create(version)
                    .withLookupString("latest")
                    .withTypeText(typeText, true);
        }
        return LookupElementBuilder.create(version).withTypeText(typeText, true);
    }

    private VersionPropsProperty findParentProperty(VersionPropsDependencyVersion versionElement) {
        return versionElement == null ? null : PsiTreeUtil.getParentOfType(versionElement, VersionPropsProperty.class);
    }

    @Override
    public final boolean invokeAutoPopup(PsiElement position, char typeChar) {
        return true;
    }

    private record DependencyInfo(DependencyGroup group, DependencyName dependencyName) {}
}
