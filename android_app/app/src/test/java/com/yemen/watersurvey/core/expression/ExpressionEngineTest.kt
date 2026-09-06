package com.yemen.watersurvey.core.expression

import com.yemen.watersurvey.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for Phase 13 / 13A Expression Engine.
 *
 * Phase 13A architectural verification:
 * - ZERO imports from com.yemen.watersurvey.presentation.*
 * - ZERO imports from androidx.compose.*
 * - Uses strongly-typed ExpressionEvaluationContext with canonical RuntimeValue instances
 *
 * Verifies:
 * 1. Field reference evaluation (${field} present / missing)
 * 2. Comparisons (=, !=, >, >=, <=)
 * 3. Logical operators (and, or, precedence)
 * 4. Arithmetic (*, div, division by zero)
 * 5. Verified functions (if, concat, uuid, today, selected, count-selected)
 * 6. Explicit rejection of unsupported constructs (<, +, /, arithmetic -)
 * 7. Real v6 expressions from Wells, Springs, and Water Harvesting forms
 * 8. FormExpressionEvaluatorImpl integration with ExpressionEvaluationContext
 */
class ExpressionEngineTest {

    private lateinit var evaluator: FormExpressionEvaluatorImpl
    private lateinit var engine: ExpressionEvaluatorEngine
    private val testSurveyUUID = "550e8400-e29b-41d4-a716-446655440000"
    private val testTodayDate = "2026-09-05"

    @Before
    fun setup() {
        evaluator = FormExpressionEvaluatorImpl(
            surveyUUID = testSurveyUUID,
            todayDate = testTodayDate
        )
        engine = ExpressionEvaluatorEngine()
    }

    /**
     * Helper to construct a strongly-typed ExpressionEvaluationContext from Pair<String, RuntimeValue>.
     */
    private fun ctxOf(
        vararg pairs: Pair<String, RuntimeValue>,
        currentFieldName: String? = null,
        currentFieldValue: RuntimeValue? = null,
        itemProperties: Map<String, String>? = null,
        surveyUUID: String? = testSurveyUUID,
        todayDate: String? = testTodayDate
    ): ExpressionEvaluationContext {
        val map = mapOf(*pairs)
        return ExpressionEvaluationContext(
            getFieldValue = { fieldName -> map[fieldName] },
            currentFieldValue = currentFieldValue,
            currentFieldName = currentFieldName,
            itemProperties = itemProperties,
            surveyUUID = surveyUUID,
            todayDate = todayDate
        )
    }

    private fun parseAndEval(
        expr: String,
        context: ExpressionEvaluationContext = ctxOf()
    ): Any? {
        val tokenizer = Tokenizer(expr)
        val tokens = tokenizer.tokenize()
        val parser = ExpressionParser(tokens)
        val ast = parser.parse()
        return engine.evaluate(ast, context)
    }

    // --- 1. FIELD REFERENCES ---
    @Test
    fun testFieldReferenceExistingAndMissing() {
        val context = ctxOf(
            "well_depth" to RuntimeValue.Decimal(150.0),
            "well_name" to RuntimeValue.Text("بئر الخير")
        )

        assertEquals(150.0, parseAndEval("\${well_depth}", context))
        assertEquals("بئر الخير", parseAndEval("\${well_name}", context))
        assertEquals("", parseAndEval("\${missing_field}", context))
    }

    // --- 2. COMPARISONS ---
    @Test
    fun testComparisons() {
        val context = ctxOf(
            "energy_type" to RuntimeValue.Choice("solar"),
            "depth" to RuntimeValue.Integer(100L)
        )

        assertEquals(true, parseAndEval("\${energy_type} = 'solar'", context))
        assertEquals(false, parseAndEval("\${energy_type} = 'diesel'", context))
        assertEquals(true, parseAndEval("\${energy_type} != 'diesel'", context))
        assertEquals(true, parseAndEval("\${depth} > 50", context))
        assertEquals(true, parseAndEval("\${depth} >= 100", context))
        assertEquals(true, parseAndEval("\${depth} <= 100", context))
        assertEquals(true, parseAndEval("\${depth} <= 200", context))
    }

    // --- 3. LOGICAL OPERATORS & PRECEDENCE ---
    @Test
    fun testLogicalOperatorsAndPrecedence() {
        val context = ctxOf(
            "is_active" to RuntimeValue.Choice("yes"),
            "depth" to RuntimeValue.Decimal(120.0),
            "facility_type" to RuntimeValue.Choice("dam")
        )

        assertEquals(true, parseAndEval("\${is_active} = 'yes' and \${depth} >= 100", context))
        assertEquals(false, parseAndEval("\${is_active} = 'yes' and \${depth} > 200", context))
        assertEquals(true, parseAndEval("\${facility_type} = 'dam' or \${facility_type} = 'barrier'", context))

        // Precedence test: (A or B and C) -> A or (B and C)
        // false or true and false -> false or (true and false) -> false
        val context2 = ctxOf(
            "a" to RuntimeValue.Choice("no"),
            "b" to RuntimeValue.Choice("yes"),
            "c" to RuntimeValue.Choice("no")
        )

        assertEquals(false, parseAndEval("\${a} = 'yes' or \${b} = 'yes' and \${c} = 'yes'", context2))
    }

    // --- 4. ARITHMETIC (*, div) & DIVISION BY ZERO ---
    @Test
    fun testArithmeticAndDivisionByZero() {
        val context = ctxOf(
            "length" to RuntimeValue.Decimal(10.0),
            "width" to RuntimeValue.Decimal(5.0),
            "count" to RuntimeValue.Integer(4L),
            "power" to RuntimeValue.Integer(250L),
            "zero" to RuntimeValue.Integer(0L)
        )

        assertEquals(50L, parseAndEval("\${length} * \${width}", context))
        assertEquals(1000L, parseAndEval("\${count} * \${power}", context))
        assertEquals(1L, parseAndEval("(\${count} * \${power}) div 1000", context))

        // Division by zero does not crash and returns deterministic empty/0 result
        val divZeroRes = parseAndEval("\${count} div \${zero}", context)
        assertEquals("", divZeroRes)
    }

    // --- 5. VERIFIED FUNCTIONS ---
    @Test
    fun testVerifiedFunctions() {
        val context = ctxOf(
            "district_code" to RuntimeValue.Choice("YE1101"),
            "district_code_other" to RuntimeValue.Text("مديرية جديدة"),
            "crops" to RuntimeValue.MultipleChoice(setOf("wheat", "maize", "other"))
        )

        // if()
        assertEquals("YE1101", parseAndEval("if(\${district_code} = 'other', \${district_code_other}, \${district_code})", context))

        val contextOther = ctxOf(
            "district_code" to RuntimeValue.Choice("other"),
            "district_code_other" to RuntimeValue.Text("مديرية جديدة"),
            "crops" to RuntimeValue.MultipleChoice(setOf("wheat", "maize", "other"))
        )
        assertEquals("مديرية جديدة", parseAndEval("if(\${district_code} = 'other', \${district_code_other}, \${district_code})", contextOther))

        // concat()
        assertEquals("YE11-YE1101-WL-001", parseAndEval("concat('YE11-', \${district_code}, '-WL-001')", context))

        // uuid()
        assertEquals(testSurveyUUID, parseAndEval("uuid()"))

        // today()
        assertEquals(testTodayDate, parseAndEval("today()"))

        // selected()
        assertEquals(true, parseAndEval("selected(\${crops}, 'wheat')", context))
        assertEquals(true, parseAndEval("selected(\${crops}, 'other')", context))
        assertEquals(false, parseAndEval("selected(\${crops}, 'barley')", context))

        // count-selected()
        assertEquals(3L, parseAndEval("count-selected(\${crops})", context))
    }

    // --- 6. UNSUPPORTED CONSTRUCTS REJECTION ---
    @Test
    fun testUnsupportedConstructsRejection() {
        // Standalone '<' operator is unsupported
        try {
            val tokenizer = Tokenizer("\${depth} < 100")
            val parser = ExpressionParser(tokenizer.tokenize())
            parser.parse()
            fail("Standalone '<' operator must be rejected")
        } catch (e: Exception) {
            assertTrue("Error message must mention unsupported operator", e.message?.contains("Unsupported") == true || e.message?.contains("<") == true)
        }

        // '+' operator is unsupported
        try {
            val tokenizer = Tokenizer("\${a} + \${b}")
            val parser = ExpressionParser(tokenizer.tokenize())
            parser.parse()
            fail("Operator '+' must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Unsupported") == true || e.message?.contains("+") == true)
        }

        // '/' operator is unsupported
        try {
            val tokenizer = Tokenizer("\${a} / \${b}")
            val parser = ExpressionParser(tokenizer.tokenize())
            parser.parse()
            fail("Operator '/' must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Unsupported") == true || e.message?.contains("/") == true)
        }

        // Unknown function is rejected
        try {
            val tokenizer = Tokenizer("unknown_fn(123)")
            val parser = ExpressionParser(tokenizer.tokenize())
            parser.parse()
            fail("Unknown function must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Unknown or unsupported function") == true)
        }
    }

    // --- 7. REAL V6 XLSFORM EXPRESSIONS ---
    @Test
    fun testRealV6WellsExpressions() {
        val context = ctxOf(
            "gov_pcode" to RuntimeValue.Choice("YE11"),
            "district_code" to RuntimeValue.Choice("YE1101"),
            "uzlah_code" to RuntimeValue.Choice("YE110101"),
            "sequence_no" to RuntimeValue.Text("0005")
        )

        // Real v6 P-code registry calculation expression
        val registryCalcExpr = "concat(\${gov_pcode},'-',if(\${district_code}='other',\${district_code_other},\${district_code}),'-',if(\${uzlah_code}='other',\${uzlah_code_other},\${uzlah_code}),'-WL-',\${sequence_no})"
        assertEquals("YE11-YE1101-YE110101-WL-0005", parseAndEval(registryCalcExpr, context))

        // Real v6 constraint expression: .>=0 and .<=1000
        val qDepth = QuestionElement(
            name = "well_depth_m",
            dataType = QuestionDataType.DECIMAL,
            labelAr = "عمق البئر",
            constraintExpr = FormExpression(".>=0 and .<=1000", ExpressionContext.CONSTRAINT),
            constraintMessageAr = "العمق يجب أن يكون بين 0 و 1000 متر"
        )

        val evalContext = ctxOf()
        assertNull(evaluator.validateConstraint(qDepth, RuntimeValue.Decimal(250.0), evalContext))
        assertEquals("العمق يجب أن يكون بين 0 و 1000 متر", evaluator.validateConstraint(qDepth, RuntimeValue.Decimal(1500.0), evalContext))
    }

    @Test
    fun testRealV6SpringsExpressions() {
        val context = ctxOf(
            "solar_panel_count" to RuntimeValue.Integer(10L),
            "solar_panel_power_w" to RuntimeValue.Integer(300L)
        )

        // Real v6 calculation expression for solar power kW
        val solarCalcExpr = "if(\${solar_panel_count}!='' and \${solar_panel_power_w}!='',(\${solar_panel_count}*\${solar_panel_power_w}) div 1000,'')"
        assertEquals(3L, parseAndEval(solarCalcExpr, context))

        // Real v6 spring relevance: ${spring_status}!='dry'
        val qFlow = QuestionElement(
            name = "discharge_lps",
            dataType = QuestionDataType.DECIMAL,
            labelAr = "معدل التدفق",
            relevantExpr = FormExpression("\${spring_status}!='dry'", ExpressionContext.RELEVANCE)
        )

        val ctxDry = ctxOf("spring_status" to RuntimeValue.Choice("dry"))
        val ctxActive = ctxOf("spring_status" to RuntimeValue.Choice("flowing"))

        assertFalse(evaluator.isElementRelevant(qFlow, ctxDry))
        assertTrue(evaluator.isElementRelevant(qFlow, ctxActive))
    }

    @Test
    fun testRealV6WaterHarvestingExpressions() {
        val context = ctxOf(
            "water_area_m2" to RuntimeValue.Decimal(500.0),
            "water_height_m" to RuntimeValue.Decimal(4.0)
        )

        // Real v6 calculation expression for water volume
        val volumeCalcExpr = "if(\${water_area_m2}!='' and \${water_height_m}!='',\${water_area_m2}*\${water_height_m},'')"
        assertEquals(2000L, parseAndEval(volumeCalcExpr, context))

        // Real v6 choice filter: district_code = ${district_code} or name = 'other'
        val ctxDistrict = ctxOf("district_code" to RuntimeValue.Choice("YE1101"))
        val qUzlah = QuestionElement(
            name = "uzlah_code",
            dataType = QuestionDataType.SELECT_ONE,
            labelAr = "العزلة",
            choiceFilterExpr = FormExpression("district_code = \${district_code} or name = 'other'", ExpressionContext.CHOICE_FILTER)
        )
        val choiceList = ChoiceList(
            listName = "uzlahs",
            items = listOf(
                ChoiceItem("YE110101", "عزلة 1"),
                ChoiceItem("YE110201", "عزلة 2 من مديرية ثانية"),
                ChoiceItem("other", "أخرى")
            )
        )

        val filtered = evaluator.filterChoices(qUzlah, choiceList, ctxDistrict)
        assertEquals(2, filtered.size)
        assertEquals(listOf("YE110101", "other"), filtered.map { it.name })
    }

    // --- 8. RUNTIME INTEGRATION ---
    @Test
    fun testRuntimeIntegrationRelevanceAndCalculations() {
        val ctxSolar = ctxOf(
            "energy_type" to RuntimeValue.Choice("solar"),
            "solar_count" to RuntimeValue.Integer(8L),
            "solar_power" to RuntimeValue.Integer(250L)
        )

        val qSolarCount = QuestionElement(
            name = "solar_count",
            dataType = QuestionDataType.INTEGER,
            labelAr = "عدد الألواح الشمسية",
            relevantExpr = FormExpression("\${energy_type}='solar'", ExpressionContext.RELEVANCE)
        )

        assertTrue(evaluator.isElementRelevant(qSolarCount, ctxSolar))

        val calcElement = CalculateElement(
            name = "total_kw",
            calculationExpr = FormExpression("(\${solar_count}*\${solar_power}) div 1000", ExpressionContext.CALCULATION)
        )

        val calcValue = evaluator.evaluateCalculation(calcElement, ctxSolar)
        assertNotNull(calcValue)
        assertTrue(calcValue is RuntimeValue.Integer)
        assertEquals(2L, (calcValue as RuntimeValue.Integer).value)
    }
}
