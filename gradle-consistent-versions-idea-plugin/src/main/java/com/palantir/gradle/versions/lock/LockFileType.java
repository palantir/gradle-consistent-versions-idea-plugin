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

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;

public final class LockFileType extends LanguageFileType {

    public static final LockFileType INSTANCE = new LockFileType();

    private LockFileType() {
        super(LockLanguage.INSTANCE);
    }

    @Override
    public String getName() {
        return "VersionsLock File";
    }

    @Override
    public String getDescription() {
        return "VersionsLock language file";
    }

    @Override
    public String getDefaultExtension() {
        return "lock";
    }

    @Override
    public Icon getIcon() {
        return FileTypes.PLAIN_TEXT.getIcon();
    }
}
