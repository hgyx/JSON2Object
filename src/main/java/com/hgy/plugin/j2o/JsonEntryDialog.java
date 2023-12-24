package com.hgy.plugin.j2o;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * 自定义对话框，允许用户输入 JSON 文本。
 *
 * @author hgy
 * @since 2023-12-24 18:15:07
 */
public class JsonEntryDialog extends JDialog {
    /**
     * 当用户点击 "确定 "按钮时调用的监听器。
     */
    interface OnOkListener {
        /**
         * 当用户点击确定按钮时调用的回调。
         *
         * @param className        在对话框中输入的类名。
         * @param jsonText         输入对话框的 JSON 文本。
         * @param generateBuilders true 如果生成的类应省略设置器并生成构建器。
         * @param useMPrefix       如果生成的字段前缀为 "m"，则为 true。
         */
        void onOk(String className, String jsonText, boolean generateBuilders, boolean useMPrefix);
    }

    /**
     * \w: 包含字母和下划线和数字0-9, 即: A-Za-z0-9_(注意\w多了一个下划线)
     */
    private static final String CLASS_NAME_REGEX = "[A-Za-z][A-Za-z\\d]*";

    // Data
    /**
     * 确认监听器
     */
    private final OnOkListener onOkListener;

    // 对话框
    private JButton buttonCancel;
    private JButton buttonOk;
    private JTextField className;
    private JPanel contentPane;
    private RSyntaxTextArea jsonText;
    /**
     * 使用M前缀
     */
    private JCheckBox useMPrefix;

    private JCheckBox generateBuilders;

    JsonEntryDialog(OnOkListener listener) {
        // 设置监听器
        onOkListener = listener;

        // 设置主要内容
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOk);

        // 设置最小对话框尺寸
        setMinimumSize(new Dimension(420, 200));

        // 添加按钮监听器
        buttonOk.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());

        // 点击十字时调用 onCancel()
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // 在 ESCAPE 时调用 onCancel()
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // 启用/禁用确定按钮
        buttonOk.setEnabled(false);
        className.getDocument().addDocumentListener(new TextChangedListener());
        jsonText.getDocument().addDocumentListener(new TextChangedListener());

        // 设置语法高亮
        jsonText.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream("/themes/dark.xml"));
            theme.apply(jsonText);
        } catch (IOException ignored) {
        }
        jsonText.setCodeFoldingEnabled(false);
    }

    private void onCancel() {
        dispose();
    }

    private void onOK() {
        onOkListener.onOk(className.getText(), jsonText.getText(), generateBuilders.isSelected(),
            useMPrefix.isSelected());
        dispose();
    }

    /**
     * 当 JSON 文本或根类文本发生变化时被调用。
     *
     * @author hgy
     * @since 2023-12-24 18:35:07
     */
    private class TextChangedListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            validate();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validate();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            validate();
        }

        /**
         * 验证类名和 JSON 文本，如果验证通过，则启用 "确定 "按钮。
         */
        private void validate() {
            String className = JsonEntryDialog.this.className.getText();
            String jsonText = JsonEntryDialog.this.jsonText.getText();

            buttonOk.setEnabled(className.matches(CLASS_NAME_REGEX) && !jsonText.isEmpty());
        }
    }
}
