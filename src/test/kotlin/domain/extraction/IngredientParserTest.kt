package domain.extraction

import com.tenmilelabs.domain.extraction.IngredientParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngredientParserTest {

    @Test
    fun parsesSimpleIngredient() {
        val result = IngredientParser.parse("2 cups flour")
        assertEquals("2", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("flour", result.name)
        assertNull(result.preparation)
    }

    @Test
    fun parsesUnicodeFractions() {
        val result = IngredientParser.parse("½ cup sugar")
        assertEquals("1/2", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("sugar", result.name)
    }

    @Test
    fun parsesMixedNumbers() {
        val result = IngredientParser.parse("1 1/2 teaspoons salt")
        assertEquals("1 1/2", result.quantity)
        assertEquals("teaspoon", result.unit)
        assertEquals("salt", result.name)
    }

    @Test
    fun parsesMixedWithUnicodeFraction() {
        val result = IngredientParser.parse("1½ cups all-purpose flour")
        assertEquals("1 1/2", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("all-purpose flour", result.name)
    }

    @Test
    fun parsesParentheticalQuantity() {
        val result = IngredientParser.parse("2 (14.5 oz) cans diced tomatoes")
        assertEquals("2 (14.5 oz)", result.quantity)
        assertEquals("can", result.unit)
        assertEquals("diced tomatoes", result.name)
    }

    @Test
    fun parsesRange() {
        val result = IngredientParser.parse("1-2 tablespoons olive oil")
        assertEquals("1-2", result.quantity)
        assertEquals("tablespoon", result.unit)
        assertEquals("olive oil", result.name)
    }

    @Test
    fun parsesRangeWithEnDash() {
        val result = IngredientParser.parse("1–2 tablespoons olive oil")
        assertEquals("1-2", result.quantity)
        assertEquals("tablespoon", result.unit)
        assertEquals("olive oil", result.name)
    }

    @Test
    fun normalizesUnits() {
        assertEquals("tablespoon", IngredientParser.parse("3 tbsp butter").unit)
        assertEquals("teaspoon", IngredientParser.parse("1 tsp vanilla").unit)
        assertEquals("ounce", IngredientParser.parse("8 oz cream cheese").unit)
        assertEquals("pound", IngredientParser.parse("2 lbs ground beef").unit)
    }

    @Test
    fun extractsPreparationAfterComma() {
        val result = IngredientParser.parse("1 cup cheese, shredded")
        assertEquals("1", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("cheese", result.name)
        assertEquals("shredded", result.preparation)
    }

    @Test
    fun extractsPreparationInParentheses() {
        val result = IngredientParser.parse("2 chicken breasts (boneless, skinless)")
        assertEquals("2", result.quantity)
        assertEquals("chicken breasts", result.name)
        assertEquals("boneless, skinless", result.preparation)
    }

    @Test
    fun handlesNoQuantity() {
        val result = IngredientParser.parse("salt and pepper to taste")
        assertNull(result.quantity)
        assertNull(result.unit)
        assertEquals("salt and pepper to taste", result.name)
    }

    @Test
    fun handlesNoUnit() {
        val result = IngredientParser.parse("3 eggs")
        assertEquals("3", result.quantity)
        assertNull(result.unit)
        assertEquals("eggs", result.name)
    }

    @Test
    fun preservesRawString() {
        val raw = "1½ cups all-purpose flour, sifted"
        val result = IngredientParser.parse(raw)
        assertEquals(raw, result.raw)
    }

    @Test
    fun handlesBlankInput() {
        val result = IngredientParser.parse("")
        assertNull(result.quantity)
        assertNull(result.unit)
    }

    @Test
    fun parsesFractionOnly() {
        val result = IngredientParser.parse("1/4 cup milk")
        assertEquals("1/4", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("milk", result.name)
    }

    @Test
    fun parsesDecimalQuantity() {
        val result = IngredientParser.parse("1.5 cups water")
        assertEquals("1.5", result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("water", result.name)
    }
}
