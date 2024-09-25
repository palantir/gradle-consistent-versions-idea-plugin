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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;

public class FolderLookupElement extends LookupElement {
    private final String lookupString;
    private String typeText;

    public FolderLookupElement(String lookupString, String typeText) {
        this.lookupString = lookupString;
        this.typeText = typeText;
    }

    public FolderLookupElement(String lookupString) {
        this.lookupString = lookupString;
        this.typeText = null;
    }

    @Override
    public final String getLookupString() {
        return lookupString;
    }

    @Override
    public final void renderElement(LookupElementPresentation presentation) {
        presentation.setItemText(lookupString);
        presentation.setTypeText(typeText);
    }

    public final void clearTypeText() {
        this.typeText = null;
    }
}
