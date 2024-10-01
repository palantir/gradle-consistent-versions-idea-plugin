package com.palantir.gradle.versions.intellij;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;

public class VersionPropsDocumentListener implements DocumentListener {
    private final FileEditor fileEditor;
    private final Editor editor;
    private final Map<String, String> originalContent;
    private final Map<String, FloatingToolbarComponent> filesToolbarComponents;
    private static final String FILE_NAME = "versions.props";

    public VersionPropsDocumentListener(
            FileEditor fileEditor,
            Editor editor,
            Map<String, String> originalContent,
            Map<String, FloatingToolbarComponent> filesToolbarComponents) {
        this.fileEditor = fileEditor;
        this.editor = editor;
        this.originalContent = originalContent;
        this.filesToolbarComponents = filesToolbarComponents;
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
        // No action needed before the document change
    }

    @Override
    public void documentChanged(DocumentEvent event) {
        VirtualFile file = fileEditor.getFile();
        if (file != null && FILE_NAME.equals(file.getName())) {
            String currentContent = editor.getDocument().getText();

            if (!originalContent.get(file.getPath()).equals(currentContent)) {
                updateFileChanged(file.getPath());
            } else {
                updateFileUnchanged(file.getPath());
            }
        }
    }

    private void updateFileChanged(String filePath) {
        FloatingToolbarComponent toolbarComponent = filesToolbarComponents.get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleShow();
        }
    }

    private void updateFileUnchanged(String filePath) {
        FloatingToolbarComponent toolbarComponent = filesToolbarComponents.get(filePath);
        if (toolbarComponent != null) {
            toolbarComponent.scheduleHide();
        }
    }
}