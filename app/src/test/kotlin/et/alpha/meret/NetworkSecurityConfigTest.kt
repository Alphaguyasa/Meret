package et.alpha.meret

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cleartext HTTP is allowed to the loopback tile server and nowhere else, and only the
 * system CAs are trusted. Sync carries personal records; it must never fall back to
 * plain HTTP or accept a user-installed CA.
 *
 * Reads the source files directly, like [BackupRulesTest].
 */
class NetworkSecurityConfigTest {
    private val config: Element by lazy {
        parse("src/main/res/xml/network_security_config.xml").documentElement
    }

    @Test
    fun `manifest uses the config and does not allow cleartext itself`() {
        val application =
            parse("src/main/AndroidManifest.xml")
                .getElementsByTagName("application")
                .item(0) as Element

        assertThat(application.getAttribute("android:networkSecurityConfig"))
            .isEqualTo("@xml/network_security_config")
        assertThat(application.hasAttribute("android:usesCleartextTraffic")).isFalse()
    }

    @Test
    fun `base config refuses cleartext and trusts system CAs only`() {
        val base = config.childElements("base-config").single()

        assertThat(base.getAttribute("cleartextTrafficPermitted")).isEqualTo("false")
        val sources = base.descendants("certificates").map { it.getAttribute("src") }
        assertThat(sources).containsExactly("system")
    }

    @Test
    fun `cleartext is allowed for 127_0_0_1 only, without subdomains`() {
        val cleartext =
            config.childElements("domain-config").filter {
                it.getAttribute("cleartextTrafficPermitted") == "true"
            }
        val domains = cleartext.flatMap { it.childElements("domain") }

        assertThat(domains.map { it.textContent.trim() }).containsExactly(LOOPBACK)
        assertThat(domains.single().getAttribute("includeSubdomains")).isEqualTo("false")
        assertThat(cleartext.single().childElements("domain-config")).isEmpty()
    }

    @Test
    fun `no user CAs and no debug overrides`() {
        val sources = config.descendants("certificates").map { it.getAttribute("src") }

        assertThat(sources).doesNotContain("user")
        assertThat(config.descendants("debug-overrides")).isEmpty()
    }

    private fun parse(path: String) = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(File(path))

    private fun Element.childElements(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tag }
    }

    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private companion object {
        /** `LocalTileServer.HOST` in `:core:maps`. */
        const val LOOPBACK = "127.0.0.1"
    }
}
