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

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.palantir.gradle.versions.intellij.psi.VersionPropsTypes;
import com.intellij.psi.TokenType;

%%

// Define the lexer class
%class VersionPropsLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{ return; }
%eof}

// Define lexer states
%state WAITING_NAME, WAITING_VERSION, WAITING_COMMENT

// Define token patterns
CRLF=\R
WHITE_SPACE=[\ \t\f]
VERSION=[^= \n\f#]+
COLON=[:]
EQUALS=[=]
DOT=[.]
IDENTIFIER = [^.:=\ \n\t\f]+
COMMENT=("#")[^\r\n]*

%%

<YYINITIAL> {WHITE_SPACE}*{COMMENT}              { return VersionPropsTypes.COMMENT; }
<YYINITIAL> {IDENTIFIER}                         { return VersionPropsTypes.GROUP_PART; }
<YYINITIAL> {DOT}                                { return VersionPropsTypes.DOT; }
<YYINITIAL> {COLON}                              { yybegin(WAITING_NAME); return VersionPropsTypes.COLON; }

<WAITING_NAME> {IDENTIFIER}                      { yybegin(WAITING_VERSION); return VersionPropsTypes.NAME_KEY; }

<WAITING_VERSION> {WHITE_SPACE}+                 { return TokenType.WHITE_SPACE; }
<WAITING_VERSION> {EQUALS}                       { return VersionPropsTypes.EQUALS; }
<WAITING_VERSION> {WHITE_SPACE}+                 { return TokenType.WHITE_SPACE; }
<WAITING_VERSION> {VERSION}                      { yybegin(WAITING_COMMENT); return VersionPropsTypes.VERSION; }

<WAITING_COMMENT> {WHITE_SPACE}*{COMMENT}        { return VersionPropsTypes.COMMENT; }

{CRLF}+                                          { yybegin(YYINITIAL); return  VersionPropsTypes.CRLF; }

[^]                                              { return TokenType.BAD_CHARACTER; }