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

%class VersionPropsLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{ return; }
%eof}

%state WAITING_NAME, WAITING_VERSION, WAITING_VALUE, INVALID_VALUE

CRLF=\R
WHITE_SPACE=[\ \n\t\f]
VALUE=[^ \n\f]+
COLON=[:]
EQUALS=[=]
DOT=[.]
KEY_CHARACTER=[^:=\ \n\t\f]+
GROUP_PART=[^.:=\ \n\t\f]+
COMMENT=("#")[^\r\n]*

%%

<YYINITIAL> {COMMENT}                            { yybegin(YYINITIAL); return VersionPropsTypes.COMMENT; }
<YYINITIAL> {GROUP_PART}                         { yybegin(YYINITIAL); return VersionPropsTypes.GROUP_PART; }
<YYINITIAL> {DOT}                                { yybegin(YYINITIAL); return VersionPropsTypes.DOT; }
<YYINITIAL> {WHITE_SPACE}*{DOT}{WHITE_SPACE}*    { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }
<YYINITIAL> {COLON}                              { yybegin(WAITING_NAME); return VersionPropsTypes.COLON; }
<YYINITIAL> {WHITE_SPACE}*{COLON}{WHITE_SPACE}*  { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_NAME> {KEY_CHARACTER}                   { yybegin(WAITING_VERSION); return VersionPropsTypes.NAME_KEY; }
<WAITING_NAME> {WHITE_SPACE}+                    { return TokenType.WHITE_SPACE; }

<WAITING_VERSION> {EQUALS}                       { yybegin(WAITING_VALUE); return VersionPropsTypes.EQUALS; }
<WAITING_VERSION> {WHITE_SPACE}+                 { return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {WHITE_SPACE}+                   { return TokenType.WHITE_SPACE; }
<WAITING_VALUE> {VALUE}                          { yybegin(INVALID_VALUE); return VersionPropsTypes.VERSION; }

<INVALID_VALUE> [^\n]+                           { return TokenType.BAD_CHARACTER; }

({CRLF}|{WHITE_SPACE})+                          { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                              { return TokenType.BAD_CHARACTER; }