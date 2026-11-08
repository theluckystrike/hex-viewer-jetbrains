package zovo.jcefshell

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RoutesTest {
    private lateinit var dir: File
    private lateinit var data: ByteArray
    private lateinit var file: File
    private val token = Routes.newToken()
    private val saved = ArrayList<String>()
    private var renders = 0

    private val host = object : HostActions {
        override fun copy(text: String) { saved.add("copy:$text") }
        override fun rendered(): Boolean { renders++; return renders >= 3 }
        override fun openUrl(url: String) { saved.add("open:$url") }
    }

    private val resources = mapOf("view.html" to "<b>{{TITLE}}</b>{{FOOTER}}<script nonce=\"{{NONCE}}\">window.ZOVO={{CONFIG}}</script>".toByteArray(), "hex.js" to "js".toByteArray())

    @Before fun setUp() {
        dir = File(System.getProperty("hex.testTmp") ?: "build/test-tmp", "routes-" + token).apply { mkdirs() }
        data = ByteArray(3 * 1024 * 1024 + 17) { (it * 31 + 7).toByte() }
        file = File(dir, "sample.bin").apply { writeBytes(data) }
        Routes.register(ViewerEntry(token, LocalFileSource(file), "sample <x>.bin", "hex", { "vscode-dark" }, { ":root{}" }, "<div>footer</div>", host))
    }

    @After fun tearDown() { Routes.unregister(token); dir.deleteRecursively() }

    private fun get(path: String) = Routes.handle("GET", Routes.ORIGIN + path, null) { resources[it] }
    private fun post(path: String, json: String) = Routes.handle("POST", Routes.ORIGIN + path, json.toByteArray()) { resources[it] }

    @Test fun rangeReturnsExactlyTheRequestedBytes() {
        for ((off, len) in listOf(0L to 8, 1L to 1, 1048570L to 4096, data.size - 5L to 5, 2_000_000L to 1_000_000, 0L to 0)) {
            val r = get("/t/$token/file?offset=$off&length=$len")
            assertEquals(200, r.status)
            assertArrayEquals("offset $off length $len", data.copyOfRange(off.toInt(), off.toInt() + len), r.body)
        }
    }

    @Test fun rangePastTheEndIs416() {
        assertEquals(416, get("/t/$token/file?offset=${data.size - 4}&length=5").status)
        assertEquals(416, get("/t/$token/file?offset=${data.size + 1}&length=1").status)
        assertEquals(416, get("/t/$token/file?offset=-1&length=4").status)
        assertEquals(416, get("/t/$token/file?offset=abc&length=4").status)
        assertEquals("bytes */${data.size}", get("/t/$token/file?offset=${data.size}&length=1").headers["Content-Range"])
        assertNull(Routes.range("0", (Routes.MAX_RANGE + 1L).toString(), Long.MAX_VALUE))
    }

    @Test fun unknownTokenAndPathsAre404() {
        assertEquals(404, get("/t/deadbeef/file?offset=0&length=1").status)
        assertEquals(404, get("/static/../plugin.xml").status)
        assertEquals(404, get("/static/missing.js").status)
        assertEquals(404, Routes.handle("GET", "http://other-host/t/$token/file", null) { null }.status)
        Routes.unregister(token)
        assertEquals(404, get("/t/$token/meta").status)
    }

    @Test fun staticMetaAndPage() {
        val js = get("/static/hex.js")
        assertEquals(200, js.status); assertEquals("text/javascript; charset=utf-8", js.mime)
        assertEquals("{\"size\":${data.size},\"name\":\"sample \\u003cx>.bin\"}", String(get("/t/$token/meta").body))
        val page = String(get("/t/$token/view.html").body)
        assertTrue(page, page.startsWith("<b>sample &lt;x&gt;.bin</b><div>footer</div>"))
        assertTrue(page, page.contains("\"name\":\"sample \\u003cx>.bin\""))
        assertTrue(page, !page.contains("{{"))
    }

    @Test fun noRouteWritesAndSaveIsGone() {
        assertEquals(404, post("/t/$token/save", "{\"name\":\"x.bin\",\"kind\":\"text\",\"data\":\"a\"}").status)
        assertEquals(405, Routes.handle("PUT", Routes.ORIGIN + "/t/$token/file?offset=0&length=1", "x".toByteArray()) { null }.status)
        assertArrayEquals(data, file.readBytes())
    }

    @Test fun renderedCountsAndAllowsTheRateLinkFromTheThirdOpen() {
        assertEquals("{\"rate\":false}", String(post("/t/$token/rendered", "{}").body))
        assertEquals("{\"rate\":false}", String(post("/t/$token/rendered", "{}").body))
        assertEquals("{\"rate\":true}", String(post("/t/$token/rendered", "{}").body))
    }

    @Test fun memorySourceServesRangesLikeAFile() {
        val t2 = Routes.newToken()
        Routes.register(ViewerEntry(t2, MemorySource(data.copyOf(70_000)), "Inner.class", "hex", { "" }, { "" }, "", host))
        try {
            assertArrayEquals(data.copyOfRange(65_536, 70_000), get("/t/$t2/file?offset=65536&length=4464").body)
            assertEquals(416, get("/t/$t2/file?offset=65536&length=4465").status)
        } finally { Routes.unregister(t2) }
    }

    @Test fun rangesAcrossThe64MbLimitAndPastFourGigabytes() {
        assertEquals(ByteRange(5_000_000_000L, Routes.MAX_RANGE), Routes.range("5000000000", Routes.MAX_RANGE.toString(), 10_737_418_240L))
        assertNull(Routes.range("10737418239", "2", 10_737_418_240L))
        assertEquals(ByteRange(10_737_418_239L, 1), Routes.range("10737418239", "1", 10_737_418_240L))
    }

    @Test fun copyAndOpenOnlyHttps() {
        post("/t/$token/copy", "{\"text\":\"tab\\there \\\"q\\\" \\u00e9\"}")
        assertEquals("copy:tab\there \"q\" \u00e9", saved.last())
        assertEquals(400, post("/t/$token/open", "{\"url\":\"file:///etc/passwd\"}").status)
        assertEquals(200, post("/t/$token/open", "{\"url\":\"https://github.com/theluckystrike\"}").status)
        assertEquals("open:https://github.com/theluckystrike", saved.last())
        assertEquals(405, get("/t/$token/copy").status)
    }
}
