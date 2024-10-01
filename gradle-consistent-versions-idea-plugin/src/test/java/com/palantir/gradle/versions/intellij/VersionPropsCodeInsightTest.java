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

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionPropsCodeInsightTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Override
    protected final String getRelativePath() {
        return "";
    }

    @Override
    protected final String getTestDataPath() {
        return "";
    }

    @Test
    public void test_version_completion() {
        JavaCodeInsightTestFixture fixture = getFixture();
        // The file name is required for context but does not need to exist on the filesystem
        fixture.configureByText("versions.props", "com.palantir.baseline:baseline-error-prone = <caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        assertThat(lookupElementStrings).isNotNull();
        UsefulTestCase.assertContainsElements(lookupElementStrings, "0.66.0", "2.40.2");
    }

    @Test
    public void test_group_completion() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        // The file name is required for context but does not need to exist on the filesystem
        fixture.configureByText("versions.props", "com.palantir.baseline.<caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        assertThat(lookupElementStrings).isNotNull();
        UsefulTestCase.assertContainsElements(lookupElementStrings, "baseline-error-prone", "baseline-null-away");
    }

    @Test
    public void test_package_completion() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        // The file name is required for context but does not need to exist on the filesystem
        fixture.configureByText("versions.props", "com.palantir.baseline:<caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        assertThat(lookupElementStrings).isNotNull();
        UsefulTestCase.assertContainsElements(lookupElementStrings, "baseline-error-prone", "baseline-null-away");
    }

    @Test
    public void test_other_file_names() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        fixture.configureByText("notVersions.props", "com.palantir.baseline:<caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        UsefulTestCase.assertEmpty(lookupElementStrings);
    }

    @Test
    public void test_psi_tree_structure() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        fixture.configureByText("versions.props", "com.palantir.baseline:baseline-error-prone=2.40.2");

        ApplicationManager.getApplication().runReadAction(() -> {
            PsiFile psiFile = fixture.getFile();
            assertThat(psiFile).isNotNull();

            PsiElement propertyElement = psiFile.getFirstChild();
            assertThat(propertyElement).isNotNull();
            assertThat(propertyElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.PROPERTY);

            PsiElement dependencyGroupElement = propertyElement.getFirstChild();
            assertThat(dependencyGroupElement).isNotNull();
            assertThat(dependencyGroupElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.DEPENDENCY_GROUP);

            PsiElement[] groupParts = PsiTreeUtil.getChildrenOfType(dependencyGroupElement, PsiElement.class);
            assertThat(groupParts).isNotNull();
            assertThat(groupParts.length >= 5).isTrue(); // Should contain at least com, ., palantir, ., baseline
            assertThat(groupParts[0].getNode().getElementType()).isEqualTo(VersionPropsTypes.GROUP_PART);
            assertThat(groupParts[0].getText()).isEqualTo("com");
            assertThat(groupParts[1].getNode().getElementType()).isEqualTo(VersionPropsTypes.DOT);
            assertThat(groupParts[1].getText()).isEqualTo(".");
            assertThat(groupParts[2].getNode().getElementType()).isEqualTo(VersionPropsTypes.GROUP_PART);
            assertThat(groupParts[2].getText()).isEqualTo("palantir");
            assertThat(groupParts[3].getNode().getElementType()).isEqualTo(VersionPropsTypes.DOT);
            assertThat(groupParts[3].getText()).isEqualTo(".");
            assertThat(groupParts[4].getNode().getElementType()).isEqualTo(VersionPropsTypes.GROUP_PART);
            assertThat(groupParts[4].getText()).isEqualTo("baseline");

            PsiElement colonElement = dependencyGroupElement.getNextSibling();
            while (colonElement != null && colonElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                colonElement = colonElement.getNextSibling();
            }
            assertThat(colonElement).isNotNull();
            assertThat(colonElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.COLON);

            PsiElement dependencyNameElement = colonElement.getNextSibling();
            while (dependencyNameElement != null
                    && dependencyNameElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                dependencyNameElement = dependencyNameElement.getNextSibling();
            }
            assertThat(dependencyNameElement).isNotNull();
            assertThat(dependencyNameElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.DEPENDENCY_NAME);

            PsiElement nameKeyElement = dependencyNameElement.getFirstChild();
            assertThat(nameKeyElement).isNotNull();
            assertThat(nameKeyElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.NAME_KEY);
            assertThat(nameKeyElement.getText()).isEqualTo("baseline-error-prone");

            PsiElement equalsElement = dependencyNameElement.getNextSibling();
            while (equalsElement != null && equalsElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                equalsElement = equalsElement.getNextSibling();
            }
            assertThat(equalsElement).isNotNull();
            assertThat(equalsElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.EQUALS);

            PsiElement dependencyVersionElement = equalsElement.getNextSibling();
            while (dependencyVersionElement != null
                    && dependencyVersionElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                dependencyVersionElement = dependencyVersionElement.getNextSibling();
            }
            assertThat(dependencyVersionElement).isNotNull();
            assertThat(dependencyVersionElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.DEPENDENCY_VERSION);

            PsiElement versionElement = dependencyVersionElement.getFirstChild();
            assertThat(versionElement).isNotNull();
            assertThat(versionElement.getNode().getElementType()).isEqualTo(VersionPropsTypes.VERSION);
            assertThat(versionElement.getText()).isEqualTo("2.40.2");
        });
    }
}
