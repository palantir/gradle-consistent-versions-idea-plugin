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

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.palantir.gradle.versions.lock.parser.LockParser;
import com.palantir.gradle.versions.lock.psi.LockFile;
import com.palantir.gradle.versions.lock.psi.LockTokenSets;
import com.palantir.gradle.versions.lock.psi.LockTypes;

public class LockParserDefinition implements ParserDefinition {

    public static final IFileElementType FILE = new IFileElementType(LockLanguage.INSTANCE);

    @Override
    public final Lexer createLexer(Project project) {
        return new LockLexerAdapter();
    }

    @Override
    public final TokenSet getCommentTokens() {
        return LockTokenSets.COMMENTS;
    }

    @Override
    public final TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public final PsiParser createParser(final Project project) {
        return new LockParser();
    }

    @Override
    public final IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public final PsiFile createFile(FileViewProvider viewProvider) {
        return new LockFile(viewProvider);
    }

    @Override
    public final PsiElement createElement(ASTNode node) {
        return LockTypes.Factory.createElement(node);
    }
}
