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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.palantir.gradle.versions.lock.psi.LockTypes;
import java.util.Collections;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class HashAnnotator implements Annotator {

    @Override
    public final void annotate(PsiElement element, AnnotationHolder holder) {
        if (element.getNode().getElementType() == LockTypes.LOCK_HASH) {

            Project project = element.getProject();
            InspectionManager inspectionManager = InspectionManager.getInstance(project);
            ProblemDescriptor problemDescriptor = inspectionManager.createProblemDescriptor(
                    element,
                    "Get hierarchy",
                    new RunGradleTaskQuickFix(element),
                    ProblemHighlightType.INFORMATION,
                    true);

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .newLocalQuickFix(new RunGradleTaskQuickFix(element), problemDescriptor)
                    .registerFix()
                    .create();
        }
    }

    private record RunGradleTaskQuickFix(PsiElement element) implements LocalQuickFix {

        @Override
        public String getName() {
            return "Get hierarchy";
        }

        @Override
        public String getFamilyName() {
            return "LockTypes Plugin";
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            ExternalSystemTaskExecutionSettings settings = createExecutionSettings(project);
            ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID);
        }

        private ExternalSystemTaskExecutionSettings createExecutionSettings(Project project) {
            ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
            settings.setExternalProjectPath(project.getBasePath());
            settings.setTaskNames(Collections.singletonList("why --hash " + element.getText()));
            settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
            return settings;
        }
    }
}
