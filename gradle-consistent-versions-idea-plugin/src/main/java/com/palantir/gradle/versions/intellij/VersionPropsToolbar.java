package com.palantir.gradle.versions.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class VersionPropsToolbar extends AbstractFloatingToolbarProvider {
    private static VersionPropsToolbar instance;
    private MessageBusConnection projectBusConnection;
    private final Map<String, FloatingToolbarComponent> filesToolbarComponents;
    private final Set<String> changedFiles;
    private final Map<String, String> originalContent;

    private static final String FILE_NAME = "versions.props";

    public VersionPropsToolbar() {
        super("VersionPropsActionGroup");
        filesToolbarComponents = new HashMap<>();
        changedFiles = new HashSet<>();
        originalContent = new HashMap<>();
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
        if (fileEditor == null || fileEditor.getFile() == null) {
            return;
        }

        Project project = dataContext.getData(PlatformDataKeys.PROJECT);
        if (project == null) {
            return;
        }

        if (projectBusConnection == null) {
            projectBusConnection = project.getMessageBus().connect();
            registerOnChangeHandlers(dataContext, fileEditor, parentDisposable);
        }

        filesToolbarComponents.put(fileEditor.getFile().getPath(), component);
        if (changedFiles.contains(fileEditor.getFile().getPath())) {
            component.scheduleShow();
        }
    }

    private void registerOnChangeHandlers(DataContext dataContext, FileEditor fileEditor, Disposable parentDisposable) {
        Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile file = fileEditor.getFile();
            if (file != null) {
                originalContent.put(file.getPath(), editor.getDocument().getText());
            }
            editor.getDocument()
                    .addDocumentListener(
                            new DocumentListener() {
                                @Override
                                public void beforeDocumentChange(@NotNull DocumentEvent event) {
                                    // No action needed before the document change
                                }

                                @Override
                                public void documentChanged(@NotNull DocumentEvent event) {
                                    VirtualFile file = fileEditor.getFile();
                                    if (file != null && FILE_NAME.equals(file.getName())) {
                                        String currentContent =
                                                editor.getDocument().getText();
                                        if (!originalContent.get(file.getPath()).equals(currentContent)) {
                                            updateFileChanged(file.getPath());
                                        } else {
                                            updateFileUnchanged(file.getPath());
                                        }
                                    }
                                }
                            },
                            parentDisposable);
        }
    }

    private void updateFileChanged(String filePath) {
        changedFiles.add(filePath);
        FloatingToolbarComponent jfrogToolBar = filesToolbarComponents.get(filePath);
        if (jfrogToolBar != null) {
            jfrogToolBar.scheduleShow();
        }
    }

    private void updateFileUnchanged(String filePath) {
        changedFiles.remove(filePath);
        FloatingToolbarComponent jfrogToolBar = filesToolbarComponents.get(filePath);
        if (jfrogToolBar != null) {
            jfrogToolBar.scheduleHide();
        }
    }

    @Override
    public boolean isApplicable(DataContext dataContext) {
        return Optional.ofNullable(dataContext.getData(CommonDataKeys.EDITOR))
                .map(Editor::getVirtualFile)
                .map(VirtualFile::getName)
                .map(FILE_NAME::equals)
                .orElse(false);
    }

    public void hideToolbarForFile(String filePath) {
        FloatingToolbarComponent toolbarComponent = filesToolbarComponents.get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleHide();
        }
    }
}