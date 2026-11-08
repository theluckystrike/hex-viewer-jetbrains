package zovo.hexviewer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A binary file type for extensions the platform does not know (.bin, .so, .dylib, .o, .wasm), so the IDE opens
 * them in the Hex tab instead of asking what they are. .dat is left out on purpose: many .dat files are text,
 * and the IDE's own content check handles them (binary .dat files still open in the Hex tab).
 */
public final class BinaryFileType implements FileType {
    public static final BinaryFileType INSTANCE = new BinaryFileType();

    private BinaryFileType() { }

    @Override public @NotNull String getName() { return "Binary Data"; }
    @Override public @NotNull String getDescription() { return "Binary file (opens in Hex Viewer)"; }
    @Override public @NotNull String getDefaultExtension() { return "bin"; }
    @Override public @Nullable Icon getIcon() { return AllIcons.FileTypes.Any_type; }
    @Override public boolean isBinary() { return true; }
}
