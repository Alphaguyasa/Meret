package et.alpha.meret.domain.waypoint

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaypointDraftTest {
    @Test
    fun `a draft with nothing typed is empty, whatever form it was under`() {
        assertThat(WaypointDraft().isEmpty).isTrue()
        assertThat(
            WaypointDraft(
                formTemplateId = "template-1",
                formVersion = 2,
                numberTexts = mapOf("people" to ""),
                showErrors = true,
            ).isEmpty,
        ).isTrue()
    }

    @Test
    fun `any one thing typed makes a draft worth keeping`() {
        listOf(
            WaypointDraft(title = "B"),
            WaypointDraft(categoryKey = "household"),
            WaypointDraft(notes = "n"),
            WaypointDraft(answersJson = """{"present":"yes"}"""),
            WaypointDraft(numberTexts = mapOf("people" to "1.")),
        ).forEach { assertThat(it.isEmpty).isFalse() }
    }
}
