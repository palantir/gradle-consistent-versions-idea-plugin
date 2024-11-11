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

import com.intellij.codeInsight.completion.BaseCompletionService;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.palantir.gradle.versions.intellij.RepositoryExplorer.VersionResults;
import com.palantir.gradle.versions.intellij.psi.VersionPropsDependencyVersion;
import com.palantir.gradle.versions.intellij.psi.VersionPropsProperty;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.AbstractMap;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;

public class VersionCompletionContributor extends CompletionContributor {

    private static final RepositoryExplorer repositoryExplorer = new RepositoryExplorer();

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

                        CompletionSorter sorter = CompletionSorter.emptySorter().weigh(new VersionWeigher());
                        CompletionResultSet sortedResultSet = resultSet.withRelevanceSorter(sorter);

                        LookupElement loadingElement = PrioritizedLookupElement.withPriority(
                                LookupElementBuilder.create("Loading Versions...")
                                        .withInsertHandler((elementContext, item) -> {
                                            // Prevent insertion
                                            elementContext
                                                    .getDocument()
                                                    .deleteString(
                                                            elementContext.getStartOffset(),
                                                            elementContext.getTailOffset());
                                        }),
                                Double.MIN_VALUE);
                        sortedResultSet.addElement(loadingElement);

                        if (!dependencyName.name().contains("*")) {
                            RepositoryLoader.loadRepositories(project)
                                    .forEach(url -> addToResults(sortedResultSet, url, group, dependencyName));
                            return;
                        }

                        StreamEx.of(RepositoryLoader.loadRepositories(project))
                                .flatMap(url -> StreamEx.of(repositoryExplorer.getGroupPartOrPackageName(group, url))
                                        .filter(pkgName -> pkgName.name()
                                                .startsWith(
                                                        dependencyName.name().replace("*", "")))
                                        .map(pkgName -> new AbstractMap.SimpleEntry<>(url, pkgName)))
                                .forEach(entry -> {
                                    String url = entry.getKey();
                                    GroupPartOrPackageName pkgName = entry.getValue();
                                    DependencyName depName = DependencyName.of(pkgName.name());
                                    addToResults(sortedResultSet, url, group, depName);
                                });
                    }

                    private void addToResults(
                            CompletionResultSet resultSet,
                            String url,
                            DependencyGroup group,
                            DependencyName dependencyName) {
                        StreamEx.of(repositoryExplorer.getVersions(group, dependencyName, url))
                                .peek(this::refreshIfContentAdded)
                                .flatMap(this::streamVersions)
                                .map(this::createLookupElement)
                                .forEach(resultSet::addElement);
                    }

                    private void refreshIfContentAdded(VersionResults versionResults) {
                        if (versionResults.contentAdded()) {
                            triggerRefresh();
                        }
                    }

                    private Stream<DependencyVersion> streamVersions(VersionResults versionResults) {
                        return versionResults.versions().stream();
                    }

                    private LookupElement createLookupElement(DependencyVersion version) {
                        if (version.isLatest()) {
                            return LookupElementBuilder.create(version)
                                    .withTypeText("Possible Latest", true)
                                    .withLookupString("latest");
                        }
                        return LookupElementBuilder.create(version);
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

    private void triggerRefresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            CompletionService completionService = CompletionService.getCompletionService();
            if (completionService == null) {
                return;
            }

            BaseCompletionService baseCompletionService = (BaseCompletionService) completionService;
            CompletionProcess completionProgress = baseCompletionService.getCurrentCompletion();
            if (completionProgress == null) {
                return;
            }

            CompletionProgressIndicator completionProgressIndicator = (CompletionProgressIndicator) completionProgress;
            completionProgressIndicator.scheduleRestart();
        });
    }
}
