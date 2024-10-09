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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentAnnotator implements Annotator {

    private static final TextAttributesKey RED_BOLD = TextAttributesKey.createTextAttributesKey(
            "DEPENDENCY_UPGRADER_OFF", new TextAttributes(JBColor.RED, null, null, null, Font.BOLD));

    private static final TextAttributesKey GREEN_BOLD = TextAttributesKey.createTextAttributesKey(
            "DEPENDENCY_UPGRADER_ON", new TextAttributes(JBColor.GREEN, null, null, null, Font.BOLD));

    private static final Pattern DEPENDENCY_UPGRADER_OFF_PATTERN =
            Pattern.compile("dependency-upgrader:OFF", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPENDENCY_UPGRADER_ON_PATTERN =
            Pattern.compile("dependency-upgrader:ON", Pattern.CASE_INSENSITIVE);

    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (element instanceof PsiComment) {
            String commentText = element.getText();
            annotatePattern(commentText, DEPENDENCY_UPGRADER_OFF_PATTERN, element, holder, RED_BOLD);
            annotatePattern(commentText, DEPENDENCY_UPGRADER_ON_PATTERN, element, holder, GREEN_BOLD);
        }
    }

    private void annotatePattern(
            String text,
            Pattern pattern,
            PsiElement element,
            AnnotationHolder holder,
            TextAttributesKey attributesKey) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            int startOffset = element.getTextRange().getStartOffset() + matcher.start();
            int endOffset = startOffset + matcher.group().length();
            TextRange textRange = new TextRange(startOffset, endOffset);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(textRange)
                    .textAttributes(attributesKey)
                    .create();
        }
    }
}