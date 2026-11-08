package zovo.hexviewer

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import zovo.jcefshell.ShellEditor
import java.io.File
import java.io.RandomAccessFile

class ProviderTest : BasePlatformTestCase() {
    private val provider = HexEditorProvider()
    private val tmp by lazy { File(System.getProperty("hex.testTmp") ?: "build/test-tmp", "provider-" + System.nanoTime()).apply { mkdirs() } }

    override fun tearDown() {
        try { tmp.deleteRecursively() } finally { super.tearDown() }
    }

    private fun local(name: String, bytes: ByteArray) = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(tmp, name).apply { writeBytes(bytes) })!!

    fun testOpensBinaryTypesButNotTextOrImages() {
        val bin = ByteArray(64) { (it * 7).toByte() }
        for (ext in listOf("bin", "so", "dylib", "o", "wasm", "BIN")) {
            val vf = local("x.$ext", bin)
            assertEquals(ext, "Binary Data", vf.fileType.name)
            assertTrue(ext, provider.accept(project, vf))
        }
        assertFalse(provider.accept(project, local("notes.txt", "hello".toByteArray())))
        assertFalse(provider.accept(project, local("data.json", "{}".toByteArray())))
        assertFalse(provider.accept(project, local("plot.dat", "1 2\n3 4\n".toByteArray())))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) + ByteArray(32)
        assertFalse(provider.accept(project, local("image.png", png)))
        assertEquals(FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR, provider.policy)
    }

    fun testOpenInHexViewerForcesAnyFile() {
        val txt = local("readme.txt", "hello".toByteArray())
        assertFalse(provider.accept(project, txt))
        txt.putUserData(HexEditorProvider.FORCED, true)
        assertTrue(provider.accept(project, txt))
        assertTrue(FileEditorProviderManager.getInstance().getProviderList(project, txt).any { it is HexEditorProvider })
    }

    fun testCreatesAndDisposesTheEditorForASparseFile() {
        val f = File(tmp, "sparse.bin")
        RandomAccessFile(f, "rw").use { it.setLength(3L shl 30) } // 3 GB, sparse: no disk blocks used
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)!!
        assertTrue(provider.accept(project, vf))
        val editor = provider.createEditor(project, vf)
        try {
            assertTrue(editor is ShellEditor)
            assertEquals("Hex", editor.name)
            assertFalse(editor.isModified)
        } finally {
            Disposer.dispose(editor)
        }
        assertEquals(3L shl 30, f.length())
    }

    fun testHexDumpFallback() {
        val d = Support.dump("ABCDEFGHIJKLMNOPQ".toByteArray())
        assertEquals("00000000  41 42 43 44 45 46 47 48  49 4A 4B 4C 4D 4E 4F 50  ABCDEFGHIJKLMNOP\n" +
            "00000010  51" + " ".repeat(48) + "Q\n", d)
        assertTrue(Support.footerHtml().contains("id=\"zovo-rate\" hidden"))
        for ((_, url) in Support.LINKS) assertTrue(url, url.startsWith("https://"))
    }
}
