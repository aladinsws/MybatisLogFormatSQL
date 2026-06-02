package com.aladinsws.plugins;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Modal dialog that displays the formatted SQL and provides a
 * "Copy & Close" button to copy it to the system clipboard.
 */
public class MybatisLogDialog extends DialogWrapper {

    private final String formattedSql;
    private JBTextArea textArea;

    public MybatisLogDialog(@Nullable Project project, @NotNull String formattedSql) {
        super(project);
        this.formattedSql = formattedSql;
        setTitle("MyBatis Log — Formatted SQL");
        setOKButtonText("Copy & Close");
        setCancelButtonText("Close");
        init();
    }

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(700, 300));

        panel.add(new JLabel("Executable SQL:"), BorderLayout.NORTH);

        textArea = new JBTextArea(formattedSql);
        textArea.setEditable(true);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected void doOKAction() {
        CopyPasteManager.getInstance().setContents(new StringSelection(textArea.getText()));
        super.doOKAction();
    }
}


