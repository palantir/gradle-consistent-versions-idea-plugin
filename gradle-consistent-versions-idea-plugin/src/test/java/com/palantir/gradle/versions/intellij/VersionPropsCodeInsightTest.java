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
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
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
}
