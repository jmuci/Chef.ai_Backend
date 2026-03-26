package domain.extraction

import com.tenmilelabs.domain.extraction.IsoDurationParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoDurationParserTest {

    @Test
    fun parsesHoursAndMinutes() {
        assertEquals(90, IsoDurationParser.parseToMinutes("PT1H30M"))
    }

    @Test
    fun parsesMinutesOnly() {
        assertEquals(45, IsoDurationParser.parseToMinutes("PT45M"))
    }

    @Test
    fun parsesHoursOnly() {
        assertEquals(60, IsoDurationParser.parseToMinutes("PT1H"))
    }

    @Test
    fun parsesDaysHoursMinutes() {
        assertEquals(1500, IsoDurationParser.parseToMinutes("P1DT1H"))
    }

    @Test
    fun parsesWithDayZero() {
        assertEquals(30, IsoDurationParser.parseToMinutes("P0DT0H30M"))
    }

    @Test
    fun parsesSecondsRoundsUp() {
        assertEquals(1, IsoDurationParser.parseToMinutes("PT30S"))
    }

    @Test
    fun parsesZeroSeconds() {
        assertEquals(0, IsoDurationParser.parseToMinutes("PT0S"))
    }

    @Test
    fun parsesPlainNumber() {
        assertEquals(30, IsoDurationParser.parseToMinutes("30"))
    }

    @Test
    fun returnsNullForBlank() {
        assertNull(IsoDurationParser.parseToMinutes(""))
        assertNull(IsoDurationParser.parseToMinutes("   "))
    }

    @Test
    fun returnsNullForNull() {
        assertNull(IsoDurationParser.parseToMinutes(null))
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(IsoDurationParser.parseToMinutes("about 30 minutes"))
    }

    @Test
    fun handlesLowercaseInput() {
        assertEquals(90, IsoDurationParser.parseToMinutes("pt1h30m"))
    }

    @Test
    fun handlesWhitespace() {
        assertEquals(45, IsoDurationParser.parseToMinutes("  PT45M  "))
    }
}
