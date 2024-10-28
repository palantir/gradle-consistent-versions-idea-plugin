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

package com.palantir.gradle.versions.lock;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.palantir.gradle.versions.lock.psi.LockTypes;

public class HashAnnotator implements Annotator {

    public static final TextAttributesKey ATTRIBUTES_KEY = TextAttributesKey.createTextAttributesKey(
            "LOCK_TYPES_HASH_KEY", DefaultLanguageHighlighterColors.OPERATION_SIGN);

    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (element.getNode().getElementType() == LockTypes.HASH) {
            TextAttributes attributes = new TextAttributes();
            attributes.setForegroundColor(JBColor.BLUE);
            attributes.setEffectColor(JBColor.RED);
            attributes.setEffectType(EffectType.LINE_UNDERSCORE);
            holder.newSilentAnnotation(com.intellij.lang.annotation.HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(ATTRIBUTES_KEY)
                    .create();
        }
    }
}
