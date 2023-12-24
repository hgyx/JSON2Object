package com.hgy.plugin.j2o;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 生成java类的对话框的action
 */
public class PopupAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        // 从event获取project
        Project project = event.getProject();
        // 从event获取VirtualFile, getData 是一个泛型方法
        VirtualFile actionFolder = event.getData(LangDataKeys.VIRTUAL_FILE);

        if (!hasDir(project, actionFolder)) {
            return;
        }
        // 获取模块源根和有效软件包名称
        VirtualFile moduleSourceRoot =
            ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(actionFolder);
        String packageName =
            ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(actionFolder);

        // 显示 JSON 对话框
        JsonEntryDialog dialog = new JsonEntryDialog((className, jsonText, generateBuilders, useMPrefix) -> {
            // 显示后台进程指示器
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "JSON2Object Class Generation", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    // 生成类
                    GenerateObject generateObject = new GenerateObject(packageName, moduleSourceRoot, indicator);
                    generateObject.generateFromJson(className, jsonText, generateBuilders, useMPrefix);

                    // 刷新用户界面
                    try {
                        Thread.sleep(100);
                        ProjectView.getInstance(project).refresh();
                        actionFolder.refresh(false, true);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        });
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }

    private boolean hasDir(Project project, VirtualFile actionFolder) {
        return project != null && actionFolder != null && actionFolder.isDirectory();
    }

    @Override
    public void update(AnActionEvent event) {
        // 获取项目和行动文件夹
        Project project = event.getProject();
        VirtualFile actionFolder = event.getData(LangDataKeys.VIRTUAL_FILE);

        if (hasDir(project, actionFolder)) {
            // 根据软件包名称是否为非空来设置可见性
            String packageName =
                ProjectRootManager.getInstance(project).getFileIndex().getPackageNameByDirectory(actionFolder);
            event.getPresentation().setVisible(packageName != null);
        } else {
            event.getPresentation().setVisible(false);
        }
    }
}
