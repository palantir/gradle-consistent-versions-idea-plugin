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
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FolderCompletionContributor extends CompletionContributor {

    private final Map<Folder, FolderLookupElement> lookupElements = new HashMap<>();

    public FolderCompletionContributor() {
        IElementType[] elementTypes = {VersionPropsTypes.GROUP_PART, VersionPropsTypes.NAME_KEY};
        for (IElementType elementType : elementTypes) {
            cacheCompletion(elementType);
            remoteCompletion(elementType);
        }
    }

    private void remoteCompletion(IElementType elementType) {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(elementType), new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                    CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                List<String> repositories = List.of("https://repo1.maven.org/maven2/");
                DependencyGroup group = DependencyGroup.groupFromParameters(parameters);

                repositories.stream()
                        .map(RepositoryExplorer::new)
                        .flatMap(repositoryExplorer -> repositoryExplorer.getFolders(group).stream())
                        .forEach(folder -> addOrUpdateElement(resultSet, folder));
            }
        });
    }

    private void cacheCompletion(IElementType elementType) {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(elementType), new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                    CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                DependencyGroup group = DependencyGroup.groupFromParameters(parameters);
                GradleCacheExplorer gradleCacheExplorer = new GradleCacheExplorer();

                gradleCacheExplorer.getFolders(group)
                        .forEach(folder -> lookupElements.computeIfAbsent(folder, f -> createCacheElement(resultSet, f)));
            }
        });
    }

    private void addOrUpdateElement(CompletionResultSet resultSet, Folder folder) {
        FolderLookupElement lookupElement = lookupElements.get(folder);
        if (lookupElement != null) {
            lookupElement.clearTypeText();
        } else {
            lookupElement = new FolderLookupElement(folder.name());
        }
        resultSet.addElement(lookupElement);
        resultSet.restartCompletionOnAnyPrefixChange();
    }

    private FolderLookupElement createCacheElement(CompletionResultSet resultSet, Folder folder) {
        FolderLookupElement lookupElement = new FolderLookupElement(folder.name(), "from cache");
        resultSet.addElement(lookupElement);
        return lookupElement;
    }
}
