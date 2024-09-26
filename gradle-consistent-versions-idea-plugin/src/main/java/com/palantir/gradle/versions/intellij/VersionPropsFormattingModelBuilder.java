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

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;

public class VersionPropsFormattingModelBuilder implements FormattingModelBuilder {
    private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, VersionPropsLanguage.INSTANCE)
                .around(VersionPropsTypes.DOT)
                .none()
                .around(VersionPropsTypes.COLON)
                .none()
                .around(VersionPropsTypes.EQUALS)
                .spaceIf(settings.getCommonSettings(VersionPropsLanguage.INSTANCE.getID())
                        .SPACE_AROUND_ASSIGNMENT_OPERATORS)
                .before(VersionPropsTypes.PROPERTY)
                .none();
    }

    @Override
    public final FormattingModel createModel(FormattingContext formattingContext) {
        final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
        ASTNode root = formattingContext.getNode();
        Block block = new VersionPropsBlock(
                root,
                Wrap.createWrap(WrapType.NONE, false),
                Alignment.createAlignment(),
                createSpaceBuilder(codeStyleSettings));
        return FormattingModelProvider.createFormattingModelForPsiFile(
                formattingContext.getContainingFile(), block, codeStyleSettings);
    }
}
