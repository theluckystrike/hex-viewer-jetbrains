package zovo.hexviewer;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zovo.jcefshell.ShellEditor;

import java.util.Locale;
import java.util.Set;

/**
 * The read-only "Hex" editor tab. It opens by itself only for binary files (.bin, .dat, .exe, .dll, .so, .dylib,
 * .o, .class, .wasm) whose file type is binary, so text files and images keep their editors. When the IDE already
 * has an editor for the file (a decompiler for .class, for example), the Hex tab comes after it
 * (PLACE_AFTER_DEFAULT_EDITOR). "Open in Hex Viewer" adds the tab to any file.
 */
public final class HexEditorProvider implements FileEditorProvider, DumbAware {
    public static final String EDITOR_TYPE_ID = "zovo-hex-viewer";
    public static final Set<String> BINARY_EXTENSIONS = Set.of("bin", "dat", "exe", "dll", "so", "dylib", "o", "class", "wasm");
    /** Set by "Open in Hex Viewer": the file gets a Hex tab whatever its type. */
    public static final Key<Boolean> FORCED = Key.create("zovo.hexviewer.forced");

    public static boolean isBinaryCandidate(@Nullable String extension, @NotNull FileType type) {
        return extension != null && BINARY_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT)) && type.isBinary();
    }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        if (file.isDirectory()) return false;
        if (Boolean.TRUE.equals(file.getUserData(FORCED))) return true;
        return isBinaryCandidate(file.getExtension(), file.getFileType());
    }

    @Override public boolean acceptRequiresReadAction() { return false; }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ShellEditor(project, file, "hex", "Hex", Support.INSTANCE.footerHtml(), Support.INSTANCE::hexDump);
    }

    @Override public @NotNull String getEditorTypeId() { return EDITOR_TYPE_ID; }
    @Override public @NotNull FileEditorPolicy getPolicy() { return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR; }
}
