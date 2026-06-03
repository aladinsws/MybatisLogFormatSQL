package com.aladinsws.plugins;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * Right-click action that appears in the editor popup menu only when
 * there is selected text. Parses the selection as MyBatis log output
 * and shows the resulting SQL in {@link MybatisLogDialog}.
 */
public class MybatisLogAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // Presentation update runs on a background thread (safe for editor data key access)
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        var editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(hasSelection);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        var selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isBlank()) return;

        var rawSql = MybatisLogParser.parse(selectedText);
        var formattedSql = rawSql.startsWith("Error:") ? rawSql : SqlFormatter.format(rawSql);
        new MybatisLogDialog(e.getProject(), formattedSql).show();
    }
}


