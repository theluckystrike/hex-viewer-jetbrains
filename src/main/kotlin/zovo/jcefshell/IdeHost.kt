package zovo.jcefshell

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/** HostActions for one editor: clipboard, the system browser, and the open counter for the quiet rating link. */
class IdeHost : HostActions {
    override fun copy(text: String) {
        ApplicationManager.getApplication().invokeLater { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
    }

    override fun openUrl(url: String) = BrowserUtil.browse(url)

    private var counted = false

    /** Counts successful opens (once per tab). The "Rate" link shows from the 3rd one on. No popup, no reminder. */
    @Synchronized
    override fun rendered(): Boolean {
        val props = PropertiesComponent.getInstance()
        var n = props.getInt(OPENS_KEY, 0)
        if (!counted) { counted = true; n += 1; props.setValue(OPENS_KEY, n, 0) }
        return n >= RATE_AFTER
    }

    companion object {
        const val OPENS_KEY = "zovo.hexviewer.opens"
        const val RATE_AFTER = 3
    }
}
