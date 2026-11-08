package zovo.hexviewer;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** "Open in Hex Viewer" in the Project view and editor tab menus: opens any file (text, image, class) in the Hex tab. */
public final class OpenInHexViewerAction extends AnAction implements DumbAware {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = files(e);
        boolean ok = e.getProject() != null && files != null && files.length > 0;
        if (ok) for (VirtualFile f : files) if (f.isDirectory()) { ok = false; break; }
        e.getPresentation().setEnabledAndVisible(ok);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = files(e);
        if (project == null || files == null) return;
        for (VirtualFile f : files) if (!f.isDirectory()) open(project, f);
    }

    private static VirtualFile[] files(AnActionEvent e) {
        VirtualFile[] many = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (many != null && many.length > 0) return many;
        VirtualFile one = e.getData(CommonDataKeys.VIRTUAL_FILE);
        return one == null ? null : new VirtualFile[]{one};
    }

    /** Adds the Hex tab to the file (reopening it when it is already open with other editors) and selects it. */
    public static void open(@NotNull Project project, @NotNull VirtualFile file) {
        FileEditorManager fem = FileEditorManager.getInstance(project);
        boolean wasForced = Boolean.TRUE.equals(file.getUserData(HexEditorProvider.FORCED));
        file.putUserData(HexEditorProvider.FORCED, Boolean.TRUE);
        if (fem.isFileOpen(file) && !wasForced && !hasHexTab(fem, file)) {
            // The open tab was built before the file had a Hex editor; close and open it again to add one.
            fem.closeFile(file);
        }
        fem.openFile(file, true);
        fem.setSelectedEditor(file, HexEditorProvider.EDITOR_TYPE_ID);
    }

    private static boolean hasHexTab(FileEditorManager fem, VirtualFile file) {
        for (var ed : fem.getAllEditors()) if (ed instanceof zovo.jcefshell.ShellEditor && file.equals(ed.getFile())) return true;
        return false;
    }

}
