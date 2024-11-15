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

import com.google.common.collect.EvictingQueue;
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
import com.palantir.gradle.versions.intellij.VersionExplorer.PackageInRepo;
import com.palantir.gradle.versions.intellij.psi.VersionPropsDependencyVersion;
import com.palantir.gradle.versions.intellij.psi.VersionPropsProperty;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import one.util.streamex.StreamEx;

public class VersionCompletionContributor extends CompletionContributor {
    private final GroupPartOrPackageNameExplorer groupPartOrPackageNameExplorer =
            GroupPartOrPackageNameExplorer.getInstance();
    private final VersionExplorer versionExplorer = VersionExplorer.getInstance();

    private final Queue<DependencyInfo> loadedDependencies = EvictingQueue.create(100);

    VersionCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(VersionPropsTypes.VERSION),
                new CompletionProvider<>() {
                    @Override
                    public void addCompletions(
                            CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                        DependencyInfo dependencyInfo = getDependencyInfo(parameters);

                        Project project = parameters.getOriginalFile().getProject();

                        CompletionSorter sorter = CompletionSorter.emptySorter().weigh(new VersionWeigher());
                        CompletionResultSet sortedResultSet = resultSet.withRelevanceSorter(sorter);

                        if (!loadedDependencies.contains(dependencyInfo)) {
                            addDisplayElement(sortedResultSet, "Loading Versions...");
                        }

                        if (!dependencyInfo.dependencyName().name().contains("*")) {
                            handleDependencyWithoutWildcard(sortedResultSet, project, dependencyInfo);
                            return;
                        }

                        List<PackageInRepo> packageInRepo = collectPackageInRepo(project, dependencyInfo);
                        addToResults(sortedResultSet, packageInRepo, dependencyInfo);
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

    private void addDisplayElement(CompletionResultSet sortedResultSet, String elementText) {
        LookupElement loadingElement = PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(elementText).withInsertHandler((elementContext, item) -> {
                    // Prevent insertion
                    elementContext
                            .getDocument()
                            .deleteString(elementContext.getStartOffset(), elementContext.getTailOffset());
                }),
                Double.MIN_VALUE);
        sortedResultSet.addElement(loadingElement);
    }

    private void handleDependencyWithoutWildcard(
            CompletionResultSet sortedResultSet, Project project, DependencyInfo dependencyInfo) {

        List<PackageInRepo> allPackages = RepositoryLoader.loadRepositories(project).stream()
                .map(url -> new PackageInRepo(dependencyInfo.group(), dependencyInfo.dependencyName(), url))
                .collect(Collectors.toList());
        addToResults(sortedResultSet, allPackages, dependencyInfo);
    }

    private List<PackageInRepo> collectPackageInRepo(Project project, DependencyInfo dependencyInfo) {

        String dependencyNamePrefix = dependencyInfo.dependencyName().name().replace("*", "");
        return StreamEx.of(RepositoryLoader.loadRepositories(project))
                .flatMap(url -> StreamEx.of(groupPartOrPackageNameExplorer.getCancelableGroupPartOrPackageName(
                                dependencyInfo.group(), url))
                        .filter(pkgName -> pkgName.name().startsWith(dependencyNamePrefix))
                        .map(pkgName -> new SimpleEntry<>(url, pkgName)))
                .map(entry -> {
                    RepositoryUrl url = entry.getKey();
                    GroupPartOrPackageName pkgName = entry.getValue();
                    DependencyName depName = DependencyName.of(pkgName.name());
                    return new PackageInRepo(dependencyInfo.group(), depName, url);
                })
                .toList();
    }

    private void addToResults(CompletionResultSet resultSet, List<PackageInRepo> packageInRepo, DependencyInfo key) {
        VersionsResults results = versionExplorer.getVersions(packageInRepo);

        results.scheduleRunnableOnCompletion(CompletionRefreshUtil::scheduleRefresh);

        Map<DependencyVersion, Long> versionCounts = results.getVersionCounts();

        if (results.hasNoVersions()) {
            addDisplayElement(resultSet, "No versions found");
            addAndRefresh(key);
        }

        if (results.hasSomeVersions()) {
            addAndRefresh(key);
        }

        long packageCount = packageInRepo.stream()
                .map(PackageInRepo::dependencyName)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        List<LookupElement> lookupElements = versionCounts.entrySet().stream()
                .map(entry -> createLookupElement(entry.getKey(), entry.getValue(), packageCount))
                .collect(Collectors.toList());

        resultSet.addAllElements(lookupElements);
    }

    private void addAndRefresh(DependencyInfo key) {
        if (!loadedDependencies.contains(key)) {
            CompletionRefreshUtil.scheduleRefresh();
            loadedDependencies.add(key);
        }
    }

    private LookupElement createLookupElement(DependencyVersion version, Long count, Long total) {
        String typeText = ((total > 1) ? count + "/" + total + " packages" : "");
        if (version.isLatest()) {
            typeText = ((total > 1) ? "latest for " : "latest") + typeText;
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
