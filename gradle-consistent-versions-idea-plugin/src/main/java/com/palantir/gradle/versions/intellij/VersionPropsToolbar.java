package com.palantir.gradle.versions.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashMap;
import java.util.Map;

public class VersionPropsToolbar extends AbstractFloatingToolbarProvider {
    private static VersionPropsToolbar instance;
    private final Map<Project, Map<String, FloatingToolbarComponent>> projectFilesToolbarComponents = new HashMap<>();
    private final Map<Project, Map<String, String>> projectOriginalContent = new HashMap<>();

    public VersionPropsToolbar() {
        super("VersionPropsActionGroup");
        instance = this;
    }

    public static VersionPropsToolbar getInstance() {
        return instance;
    }

    @Override
    public boolean getAutoHideable() {
        return false;
    }

    @Override
    public void register(DataContext dataContext, FloatingToolbarComponent component, Disposable parentDisposable) {
        super.register(dataContext, component, parentDisposable);

        FileEditor fileEditor = dataContext.getData(PlatformDataKeys.FILE_EDITOR);
        Project project = dataContext.getData(PlatformDataKeys.PROJECT);

        if (fileEditor == null || fileEditor.getFile() == null || project == null) {
            return;
        }

        projectFilesToolbarComponents.computeIfAbsent(project, k -> new HashMap<>());
        projectOriginalContent.computeIfAbsent(project, k -> new HashMap<>());

        registerOnChangeHandlers(dataContext, fileEditor, parentDisposable, project);

        projectFilesToolbarComponents.get(project).put(fileEditor.getFile().getPath(), component);
    }

    private void registerOnChangeHandlers(
            DataContext dataContext, FileEditor fileEditor, Disposable parentDisposable, Project project) {
        Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile file = fileEditor.getFile();
            if (file != null) {
                projectOriginalContent
                        .get(project)
                        .put(file.getPath(), editor.getDocument().getText());
            }
            editor.getDocument()
                    .addDocumentListener(
                            new VersionPropsDocumentListener(
                                    fileEditor,
                                    editor,
                                    projectOriginalContent.get(project),
                                    projectFilesToolbarComponents.get(project)),
                            parentDisposable);
        }
    }

    public final void hideToolbarForFile(String filePath, Project project, DataContext dataContext) {
        FloatingToolbarComponent toolbarComponent = projectFilesToolbarComponents
                .getOrDefault(project, new HashMap<>())
                .get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleHide();
        }

        Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            projectOriginalContent
                    .get(project)
                    .put(filePath, editor.getDocument().getText());
        }
    }
}