package et.alpha.meret.core.model.form

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormAnswersJsonTest {
    private val schema = parseFixture("all_types.json")

    @Test
    fun `every answer type survives a round trip`() {
        val answers =
            mapOf(
                "name" to FormAnswer.Text("አበበ"),
                "count" to FormAnswer.Number(4.0),
                "crop" to FormAnswer.Choice("teff"),
                "inputs" to FormAnswer.Choices(listOf("compost", "seed")),
                "planted" to FormAnswer.Date("2026-07-14"),
                "photos" to FormAnswer.Photos(listOf("p-1", "p-2")),
                "corner" to FormAnswer.Point(9.0301, 38.7402, 4.5),
            )

        val decoded = FormAnswersJson.decode(FormAnswersJson.encode(answers), schema)

        assertThat(decoded).isEqualTo(FormAnswersJson.Decoded.Answers(answers))
    }

    @Test
    fun `the stored shapes are the documented ones`() {
        val json =
            FormAnswersJson.encode(
                mapOf(
                    "inputs" to FormAnswer.Choices(listOf("seed")),
                    "corner" to FormAnswer.Point(9.5, 38.5, null),
                ),
            )

        assertThat(json).isEqualTo("""{"inputs":["seed"],"corner":{"lat":9.5,"lon":38.5}}""")
    }

    @Test
    fun `answers the schema cannot type are kept raw and written back unchanged`() {
        val stored =
            """{"signature":{"strokes":[1,2]},"gone_field":"old","count":"four",""" +
                """"inputs":["seed",3],"corner":{"lat":9.0},"crop":7}"""

        val decoded = FormAnswersJson.decode(stored, schema) as FormAnswersJson.Decoded.Answers

        assertThat(decoded.answers.values.all { it is FormAnswer.Raw }).isTrue()
        assertThat(FormAnswersJson.encode(decoded.answers)).isEqualTo(stored)
    }

    @Test
    fun `without a schema every answer is raw, and none is lost`() {
        val stored = """{"a":"x","b":2}"""

        val decoded =
            FormAnswersJson.decode(
                stored,
                schema = null,
            ) as FormAnswersJson.Decoded.Answers

        assertThat(decoded.answers)
            .containsExactly("a", FormAnswer.Raw("\"x\""), "b", FormAnswer.Raw("2"))
    }

    @Test
    fun `no form data is no answers, and text that is not an object is unreadable, not empty`() {
        assertThat(
            FormAnswersJson.decode(null, schema),
        ).isEqualTo(FormAnswersJson.Decoded.Answers(emptyMap()))
        assertThat(
            FormAnswersJson.decode(" ", schema),
        ).isEqualTo(FormAnswersJson.Decoded.Answers(emptyMap()))
        assertThat(
            FormAnswersJson.decode("[1]", schema),
        ).isEqualTo(FormAnswersJson.Decoded.Unreadable)
        assertThat(
            FormAnswersJson.decode("{oops", schema),
        ).isEqualTo(FormAnswersJson.Decoded.Unreadable)
    }
}
