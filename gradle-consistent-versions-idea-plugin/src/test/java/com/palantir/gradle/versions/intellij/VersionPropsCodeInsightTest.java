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
        Assertions.assertNotNull(lookupElementStrings);
        UsefulTestCase.assertContainsElements(lookupElementStrings, "0.66.0", "2.40.2");
    }

    @Test
    public void test_group_completion() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        // The file name is required for context but does not need to exist on the filesystem
        fixture.configureByText("versions.props", "com.palantir.baseline.<caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        Assertions.assertNotNull(lookupElementStrings);
        UsefulTestCase.assertContainsElements(lookupElementStrings, "baseline-error-prone", "baseline-null-away");
    }

    @Test
    public void test_package_completion() throws Exception {
        JavaCodeInsightTestFixture fixture = getFixture();
        // The file name is required for context but does not need to exist on the filesystem
        fixture.configureByText("versions.props", "com.palantir.baseline:<caret>");
        fixture.complete(CompletionType.BASIC);
        List<String> lookupElementStrings = fixture.getLookupElementStrings();
        Assertions.assertNotNull(lookupElementStrings);
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
            Assertions.assertNotNull(psiFile);

            PsiElement propertyElement = psiFile.getFirstChild();
            Assertions.assertNotNull(propertyElement);
            Assertions.assertEquals(
                    VersionPropsTypes.PROPERTY, propertyElement.getNode().getElementType());

            PsiElement dependencyGroupElement = propertyElement.getFirstChild();
            Assertions.assertNotNull(dependencyGroupElement);
            Assertions.assertEquals(
                    VersionPropsTypes.DEPENDENCY_GROUP,
                    dependencyGroupElement.getNode().getElementType());

            PsiElement[] groupParts = PsiTreeUtil.getChildrenOfType(dependencyGroupElement, PsiElement.class);
            Assertions.assertNotNull(groupParts);
            Assertions.assertTrue(groupParts.length >= 5); // Should contain at least com, ., palantir, ., baseline
            Assertions.assertEquals(
                    VersionPropsTypes.GROUP_PART, groupParts[0].getNode().getElementType());
            Assertions.assertEquals("com", groupParts[0].getText());
            Assertions.assertEquals(
                    VersionPropsTypes.DOT, groupParts[1].getNode().getElementType());
            Assertions.assertEquals(".", groupParts[1].getText());
            Assertions.assertEquals(
                    VersionPropsTypes.GROUP_PART, groupParts[2].getNode().getElementType());
            Assertions.assertEquals("palantir", groupParts[2].getText());
            Assertions.assertEquals(
                    VersionPropsTypes.DOT, groupParts[3].getNode().getElementType());
            Assertions.assertEquals(".", groupParts[3].getText());
            Assertions.assertEquals(
                    VersionPropsTypes.GROUP_PART, groupParts[4].getNode().getElementType());
            Assertions.assertEquals("baseline", groupParts[4].getText());

            PsiElement colonElement = dependencyGroupElement.getNextSibling();
            while (colonElement != null && colonElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                colonElement = colonElement.getNextSibling();
            }
            Assertions.assertNotNull(colonElement);
            Assertions.assertEquals(
                    VersionPropsTypes.COLON, colonElement.getNode().getElementType());

            PsiElement dependencyNameElement = colonElement.getNextSibling();
            while (dependencyNameElement != null
                    && dependencyNameElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                dependencyNameElement = dependencyNameElement.getNextSibling();
            }
            Assertions.assertNotNull(dependencyNameElement);
            Assertions.assertEquals(
                    VersionPropsTypes.DEPENDENCY_NAME,
                    dependencyNameElement.getNode().getElementType());

            PsiElement nameKeyElement = dependencyNameElement.getFirstChild();
            Assertions.assertNotNull(nameKeyElement);
            Assertions.assertEquals(
                    VersionPropsTypes.NAME_KEY, nameKeyElement.getNode().getElementType());
            Assertions.assertEquals("baseline-error-prone", nameKeyElement.getText());

            PsiElement equalsElement = dependencyNameElement.getNextSibling();
            while (equalsElement != null && equalsElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                equalsElement = equalsElement.getNextSibling();
            }
            Assertions.assertNotNull(equalsElement);
            Assertions.assertEquals(
                    VersionPropsTypes.EQUALS, equalsElement.getNode().getElementType());

            PsiElement dependencyVersionElement = equalsElement.getNextSibling();
            while (dependencyVersionElement != null
                    && dependencyVersionElement.getNode().getElementType() == TokenType.WHITE_SPACE) {
                dependencyVersionElement = dependencyVersionElement.getNextSibling();
            }
            Assertions.assertNotNull(dependencyVersionElement);
            Assertions.assertEquals(
                    VersionPropsTypes.DEPENDENCY_VERSION,
                    dependencyVersionElement.getNode().getElementType());

            PsiElement versionElement = dependencyVersionElement.getFirstChild();
            Assertions.assertNotNull(versionElement);
            Assertions.assertEquals(
                    VersionPropsTypes.VERSION, versionElement.getNode().getElementType());
            Assertions.assertEquals("2.40.2", versionElement.getText());
        });
    }
}
