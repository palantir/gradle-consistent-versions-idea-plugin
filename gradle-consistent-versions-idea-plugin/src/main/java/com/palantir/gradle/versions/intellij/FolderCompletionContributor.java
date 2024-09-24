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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class FolderCompletionContributor extends CompletionContributor {

    private Map<Folder, FolderLookupElement> lookupElements = new HashMap<>();

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

                List<String> repositories = List.of("https://repo1.maven.org/maven2/");

                DependencyGroup group = DependencyGroup.groupFromParameters(parameters);

                repositories.stream()
                        .map(RepositoryExplorer::new)
                        .flatMap(repositoryExplorer -> repositoryExplorer.getFolders(group).stream()).forEach(folder -> {
                            FolderLookupElement lookupElement = lookupElements.get(folder);
                            if (lookupElement != null) {
                                lookupElement.setTypeText("");
                            } else {
                                lookupElement = new FolderLookupElement(folder.name(), "");
                            }
                            resultSet.addElement(lookupElement);
                        });
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
                gradleCacheExplorer.getFolders(group).forEach(folder -> {
                    if (!lookupElements.containsKey(folder)) {
                        FolderLookupElement lookupElement = new FolderLookupElement(folder.name(), "from cache");
                        lookupElements.put(folder, lookupElement);
                        resultSet.addElement(lookupElement);
                    }
                });
            }
        });
    }

    private static class FolderLookupElement extends LookupElement {
        private final String lookupString;
        private String typeText;

        FolderLookupElement(String lookupString, String typeText) {
            this.lookupString = lookupString;
            this.typeText = typeText;
        }

        @NotNull
        @Override
        public String getLookupString() {
            return lookupString;
        }

        @Override
        public void renderElement(@NotNull LookupElementPresentation presentation) {
            presentation.setItemText(lookupString);
            presentation.setTypeText(typeText);
        }

        public void setTypeText(String typeText) {
            this.typeText = typeText;
        }
    }
}
