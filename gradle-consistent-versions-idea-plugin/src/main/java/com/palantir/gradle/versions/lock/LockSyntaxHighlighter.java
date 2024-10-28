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

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.palantir.gradle.versions.lock.psi.LockTypes;

public class LockSyntaxHighlighter extends SyntaxHighlighterBase {

    private static final TextAttributesKey[] SEPARATOR_KEYS =
            new TextAttributesKey[] {DefaultLanguageHighlighterColors.OPERATION_SIGN};
    private static final TextAttributesKey[] CLASS_COLOR_KEYS =
            new TextAttributesKey[] {DefaultLanguageHighlighterColors.CLASS_NAME};
    private static final TextAttributesKey[] METHOD_COLOR_KEYS =
            new TextAttributesKey[] {DefaultLanguageHighlighterColors.STATIC_METHOD};
    private static final TextAttributesKey[] STRING_COLOR_KEYS =
            new TextAttributesKey[] {DefaultLanguageHighlighterColors.STRING};
    private static final TextAttributesKey[] COMMENT_KEYS =
            new TextAttributesKey[] {DefaultLanguageHighlighterColors.LINE_COMMENT};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @Override
    public final Lexer getHighlightingLexer() {
        return new LockLexerAdapter();
    }

    @Override
    public final TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(LockTypes.COLON)
                || tokenType.equals(LockTypes.OPEN_BRACKET)
                || tokenType.equals(LockTypes.CLOSE_BRACKET)) {
            return SEPARATOR_KEYS;
        }
        if (tokenType.equals(LockTypes.GROUP)) {
            return CLASS_COLOR_KEYS;
        }
        if (tokenType.equals(LockTypes.NAME) || tokenType.equals(LockTypes.HASH)) {
            return METHOD_COLOR_KEYS;
        }
        if (tokenType.equals(LockTypes.VERSION)) {
            return STRING_COLOR_KEYS;
        }
        if (tokenType.equals(LockTypes.COMMENT)) {
            return COMMENT_KEYS;
        }
        return EMPTY_KEYS;
    }
}
