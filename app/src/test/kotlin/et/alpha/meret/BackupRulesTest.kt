package et.alpha.meret

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Survey records identify people. None of the app's storage may leave the device through
 * Android backup — cloud or device-to-device. The only way records leave is sync.
 *
 * These read the source files directly: Gradle runs unit tests with the module directory
 * as the working directory.
 */
class BackupRulesTest {
    @Test
    fun `manifest disables backup`() {
        val application =
            parse("src/main/AndroidManifest.xml")
                .getElementsByTagName("application")
                .item(0) as Element

        assertThat(application.getAttribute("android:allowBackup")).isEqualTo("false")
        assertThat(application.getAttribute("android:dataExtractionRules"))
            .isEqualTo("@xml/data_extraction_rules")
        assertThat(application.getAttribute("android:fullBackupContent"))
            .isEqualTo("@xml/backup_rules")
    }

    @Test
    fun `cloud backup excludes every domain`() {
        assertThat(excludedDomains("cloud-backup")).containsAtLeastElementsIn(ALL_DOMAINS)
        assertThat(includes("cloud-backup")).isEmpty()
    }

    @Test
    fun `device transfer excludes every domain`() {
        assertThat(excludedDomains("device-transfer")).containsAtLeastElementsIn(ALL_DOMAINS)
        assertThat(includes("device-transfer")).isEmpty()
    }

    @Test
    fun `legacy full backup excludes every domain`() {
        val root = parse("src/main/res/xml/backup_rules.xml").documentElement

        val excluded = root.childElements("exclude").filter { it.isWholeDomain() }.map { it.domain }
        assertThat(excluded).containsAtLeastElementsIn(ALL_DOMAINS)
        assertThat(root.childElements("include")).isEmpty()
    }

    private fun section(name: String): Element {
        val sections =
            parse("src/main/res/xml/data_extraction_rules.xml")
                .getElementsByTagName(name)
        assertThat(sections.length).isEqualTo(1)
        return sections.item(0) as Element
    }

    private fun excludedDomains(sectionName: String): List<String> = section(sectionName)
        .childElements("exclude")
        .filter { it.isWholeDomain() }
        .map { it.domain }

    private fun includes(sectionName: String): List<Element> =
        section(sectionName).childElements("include")

    private fun parse(path: String) = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(File(path))

    private fun Element.childElements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val Element.domain: String get() = getAttribute("domain")

    private fun Element.isWholeDomain(): Boolean = getAttribute("path") == "."

    private companion object {
        val ALL_DOMAINS = listOf("root", "file", "database", "sharedpref", "external")
    }
}
