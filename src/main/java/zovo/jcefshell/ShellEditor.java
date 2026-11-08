package zovo.jcefshell;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.function.Function;

/**
 * Read-only editor tab that shows a bundled web viewer in JCEF. The page is served from the private origin
 * http://zovo-hex/ (see ShellScheme); the file is read by byte range, never sent whole.
 * Local files are read straight from disk. Files in JARs and other non-local file systems are read into
 * memory once, up to MAX_NON_LOCAL bytes. Without JCEF it shows a plain panel built by the fallback function.
 */
public final class ShellEditor extends UserDataHolderBase implements FileEditor {
    public static final long MAX_NON_LOCAL = 256L << 20;
    private final VirtualFile file;
    private final String name;
    private final JComponent component;
    private final @Nullable JBCefBrowser browser;
    private final @Nullable String token;

    public ShellEditor(@NotNull Project project, @NotNull VirtualFile file, @NotNull String kind, @NotNull String editorName,
                       @NotNull String footerHtml, @NotNull Function<VirtualFile, String> fallbackText) {
        this.file = file;
        this.name = editorName;
        ByteSource source = null;
        String problem = null;
        if (file.isInLocalFileSystem()) source = new LocalFileSource(new File(file.getPath()));
        else if (file.getLength() <= MAX_NON_LOCAL) {
            try { source = new MemorySource(file.contentsToByteArray(false)); }
            catch (Exception e) { problem = "The file could not be read: " + e.getMessage(); }
        } else problem = "Files outside the local disk open up to 256 MB. This one is " + String.format("%,d", file.getLength()) + " bytes.";
        if (JBCefApp.isSupported() && source != null) {
            ShellScheme.INSTANCE.ensureRegistered();
            token = Routes.INSTANCE.newToken();
            Routes.INSTANCE.register(new ViewerEntry(token, source, file.getName(), kind,
                    ShellTheme.INSTANCE::bodyClass, ShellTheme.INSTANCE::css, footerHtml, new IdeHost()));
            browser = JBCefBrowser.createBuilder().setCreateImmediately(false).build();
            browser.getJBCefClient().addRequestHandler(ShellScheme.INSTANCE.requestHandler(), browser.getCefBrowser());
            browser.loadURL(Routes.ORIGIN + "/t/" + token + "/view.html");
            component = browser.getComponent();
            ApplicationManager.getApplication().getMessageBus().connect(this)
                    .subscribe(LafManagerListener.TOPIC, (LafManagerListener) src -> browser.getCefBrowser().reloadIgnoreCache());
        } else {
            token = null;
            browser = null;
            component = fallbackPanel(file, problem, fallbackText);
        }
    }

    private static JComponent fallbackPanel(VirtualFile vf, @Nullable String problem, Function<VirtualFile, String> fallbackText) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(JBUI.Borders.empty(12));
        String why = problem != null ? escape(problem)
                : "The full viewer needs JCEF, which this IDE does not have (remote development or some Linux setups). The first 4 KB are shown below.";
        JBLabel title = new JBLabel("<html><b>" + escape(vf.getName()) + "</b> (" + String.format("%,d", vf.getLength())
                + " bytes)<br>" + why + "</html>");
        panel.add(title, BorderLayout.NORTH);
        JTextArea text = new JTextArea(problem != null ? "" : fallbackText.apply(vf));
        text.setEditable(false);
        text.setFont(JBUI.Fonts.create(Font.MONOSPACED, 12));
        panel.add(new JBScrollPane(text), BorderLayout.CENTER);
        return panel;
    }

    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public @Nullable String getToken() { return token; }
    public @Nullable JBCefBrowser getBrowser() { return browser; }

    @Override public @NotNull JComponent getComponent() { return component; }
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return component; }
    @Override public @NotNull String getName() { return name; }
    @Override public void setState(@NotNull FileEditorState state) { }
    @Override public boolean isModified() { return false; }
    @Override public boolean isValid() { return file.isValid(); }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public @NotNull VirtualFile getFile() { return file; }

    @Override
    public void dispose() {
        if (token != null) Routes.INSTANCE.unregister(token);
        if (browser != null) Disposer.dispose(browser);
    }
}
