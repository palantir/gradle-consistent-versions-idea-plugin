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

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.palantir.gradle.versions.lock.psi.LockTypes;
import com.intellij.psi.TokenType;

%%

// Define the lexer class
%class LockLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{ return; }
%eof}

// Define lexer states
%state WAITING_NAME, WAITING_VERSION, WAITING_CONSTRAINT_COUNT, WAITING_CONSTRAINTS, WAITING_HASH, INVALID_VALUE

// Define token patterns
CRLF=\R
WHITE_SPACE=[ \n\t\f]
COLON=[:]
OPEN_BRACKET=[\(]
CLOSE_BRACKET=[\)]
KEY=[^:()\ \n\t\f]+
COMMENT=("#")[^\r\n]*
TEST_DEPENDENCIES=("[")[^\r\n]*

%%

<YYINITIAL> {WHITE_SPACE}*{COMMENT}                   { yybegin(YYINITIAL); return LockTypes.COMMENT; }
<YYINITIAL> {WHITE_SPACE}*{TEST_DEPENDENCIES}         { yybegin(YYINITIAL); return LockTypes.TEST_DEPENDENCIES; }
<YYINITIAL> {KEY}                                     { yybegin(YYINITIAL); return LockTypes.GROUP; }
<YYINITIAL> {COLON}                                   { yybegin(WAITING_NAME); return LockTypes.COLON; }
<YYINITIAL> {WHITE_SPACE}*{COLON}{WHITE_SPACE}*       { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_NAME> {KEY}                                  { yybegin(WAITING_NAME); return LockTypes.NAME; }
<WAITING_NAME> {COLON}                                { yybegin(WAITING_VERSION); return LockTypes.COLON; }
<WAITING_NAME> {WHITE_SPACE}*{COLON}{WHITE_SPACE}*    { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_VERSION> {KEY}                               { yybegin(WAITING_VERSION); return LockTypes.VERSION; }
<WAITING_VERSION> {WHITE_SPACE}{OPEN_BRACKET}         { yybegin(WAITING_CONSTRAINT_COUNT); return LockTypes.OPEN_BRACKET; }
<WAITING_VERSION> {WHITE_SPACE}*{COLON}{WHITE_SPACE}* { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_CONSTRAINT_COUNT> {KEY}                      { yybegin(WAITING_CONSTRAINT_COUNT); return LockTypes.CONSTRAINT_COUNT; }
<WAITING_CONSTRAINT_COUNT> {WHITE_SPACE}              { yybegin(WAITING_CONSTRAINTS); return TokenType.WHITE_SPACE; }
<WAITING_CONSTRAINT_COUNT> {WHITE_SPACE}*{CLOSE_BRACKET} { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_CONSTRAINTS> {KEY}                           { yybegin(WAITING_CONSTRAINTS); return LockTypes.CONSTRAINTS; }
<WAITING_CONSTRAINTS> {COLON}{WHITE_SPACE}            { yybegin(WAITING_HASH); return LockTypes.COLON; }
<WAITING_CONSTRAINTS> {WHITE_SPACE}*{CLOSE_BRACKET}   { yybegin(INVALID_VALUE); return TokenType.BAD_CHARACTER; }

<WAITING_HASH> {KEY}                                  { yybegin(WAITING_HASH); return LockTypes.HASH; }
<WAITING_HASH> {CLOSE_BRACKET}                        { yybegin(WAITING_HASH); return LockTypes.CLOSE_BRACKET; }

{CRLF}+                                               { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                                   { return TokenType.BAD_CHARACTER; }