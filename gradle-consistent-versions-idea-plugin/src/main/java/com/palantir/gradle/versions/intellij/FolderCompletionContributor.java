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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.List;

public class FolderCompletionContributor extends CompletionContributor {

    private static final GradleCacheExplorer gradleCacheExplorer =
            new GradleCacheExplorer(List.of("https://repo.maven.apache.org/maven2/"));

    public FolderCompletionContributor() {
        cacheCompletion(VersionPropsTypes.GROUP_PART);
        cacheCompletion(VersionPropsTypes.NAME_KEY);
        remoteCompletion(VersionPropsTypes.GROUP_PART);
        remoteCompletion(VersionPropsTypes.NAME_KEY);
    }

    private void remoteCompletion(IElementType elementType) {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(elementType), new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                    CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                List<String> repositories = List.of("https://repo.maven.apache.org/maven2/");

                DependencyGroup group = DependencyGroup.groupFromParameters(parameters);

                repositories.stream()
                        .map(RepositoryExplorer::new)
                        .flatMap(repositoryExplorer -> repositoryExplorer.getFolders(group).stream())
                        .map(LookupElementBuilder::create)
                        .forEach(resultSet::addElement);
            }
        });
    }

    private void cacheCompletion(IElementType elementType) {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(elementType), new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                    CompletionParameters parameters, ProcessingContext context, CompletionResultSet resultSet) {

                DependencyGroup group = DependencyGroup.groupFromParameters(parameters);

                gradleCacheExplorer.getCompletions(group).stream()
                        .map(suggestion -> LookupElementBuilder.create(Folder.of(suggestion)))
                        .forEach(resultSet::addElement);
            }
        });
    }

    @Override
    public final boolean invokeAutoPopup(PsiElement position, char typeChar) {
        return typeChar == ':';
    }
}
