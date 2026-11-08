package zovo.hexviewer

import com.intellij.openapi.vfs.VirtualFile

/** "Support and more tools" row (owner rule) and the no-JCEF hex dump. PayPal is added only when factory/sprint/funding.json has it; it is empty now. */
object Support {
    const val REPO = "https://github.com/theluckystrike/hex-viewer-jetbrains"
    const val RATE = "https://plugins.jetbrains.com/plugin/index?xmlId=zovo.hex-viewer"
    val LINKS = listOf(
        "Sponsor on GitHub" to "https://github.com/sponsors/theluckystrike",
        "GitHub" to REPO,
        "Hex Viewer for VS Code" to "https://marketplace.visualstudio.com/items?itemName=zovo.zovo-hex-viewer",
        "DOCX & PPTX Viewer" to "https://plugins.jetbrains.com/plugin/35878-docx--pptx-viewer",
        "NetCDF, HDF5 & NumPy Viewer" to "https://plugins.jetbrains.com/plugin/35879-netcdf-hdf5--numpy-viewer",
        "More Zovo tools" to "https://github.com/theluckystrike",
    )

    private fun link(label: String, url: String, extra: String = "") =
        "<a href=\"" + url + "\"" + extra + ">" + label.replace("&", "&amp;") + "</a>"

    /** Shown in the viewer's status bar. The Rate link stays hidden until the 3rd file opened without an error. */
    fun footerHtml(): String {
        val sep = "<span class=\"sep\" aria-hidden=\"true\">·</span>"
        return "<span class=\"zovo-support\" id=\"zovo-support\">" +
            LINKS.joinToString(sep) { (label, url) -> link(label, url) } +
            "<span class=\"sep zovo-rate-sep\" aria-hidden=\"true\" hidden>·</span>" +
            link("Rate on JetBrains Marketplace", RATE, " id=\"zovo-rate\" hidden title=\"Rate or review Hex Viewer\"") +
            "</span>"
    }

    /** The first 4 KB as offset, hex and ASCII columns, for IDEs without JCEF. */
    fun hexDump(file: VirtualFile): String = try {
        val head = file.inputStream.use { it.readNBytes(4096) }
        dump(head)
    } catch (e: Exception) {
        "The file could not be read: " + e.message
    }

    fun dump(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (row in bytes.indices step 16) {
            sb.append("%08X  ".format(row))
            for (i in row until row + 16) {
                if (i < bytes.size) sb.append("%02X ".format(bytes[i].toInt() and 0xff)) else sb.append("   ")
                if (i == row + 7) sb.append(' ')
            }
            sb.append(' ')
            for (i in row until minOf(row + 16, bytes.size)) {
                val c = bytes[i].toInt() and 0xff
                sb.append(if (c in 0x20..0x7e) c.toChar() else '.')
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
