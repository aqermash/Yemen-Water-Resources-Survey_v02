package com.yemen.watersurvey.core.form

import com.yemen.watersurvey.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 12A Comprehensive Unit Test Suite for JSON Form Definition & Choice List Parser.
 *
 * Implements strict verification of:
 * - Test 1: Full hierarchy parsing (Wells form structure with nested groups and elements)
 * - Test 2: Choice lists parsing with Arabic and English labels
 * - Test 3: Admin tier binding parsing (province/district/uzlah/village to AdminTier)
 * - Test 4: Calculation & Hidden elements parsing
 * - Test 5: System timestamp elements (start and end)
 * - Test 6: Dependency extraction (${field} references across expressions)
 * - Test 7: Self-reference handling ('.' in constraints does not create external dependency)
 * - Test 8: Cycle detection (circular variable dependency rejection with INVALID_PACKAGE_STRUCTURE)
 * - Test 9: Duplicate element name detection
 * - Test 10: Missing choice list reference detection
 * - Test 11: End-to-end package hydration from directory
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class FormDefinitionParserTest {

    private lateinit var parser: FormDefinitionParser
    private lateinit var validator: FormPackageValidator
    private lateinit var tempDir: File

    @Before
    fun setup() {
        parser = FormDefinitionParser()
        validator = FormPackageValidator(parser)
        tempDir = File(System.getProperty("java.io.tmpdir"), "parser_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @Test
    fun test1_FullHierarchyParsing() {
        val json = """
        {
          "formId": "yem_water_wells_saadah",
          "version": "2026-09-05-v6",
          "schemaVersion": "1.0",
          "title": "استمارة حصر وتوثيق آبار المياه - صعدة",
          "defaultLanguage": "ar",
          "instanceNameExpression": "concat(${'$'}{water_facility_name}, ' - ', ${'$'}{gov_pcode})",
          "elements": [
            { "name": "start", "type": "start" },
            { "name": "end", "type": "end" },
            {
              "name": "group_location",
              "type": "group",
              "labelAr": "البيانات الجغرافية والإدارية",
              "labelEn": "Geographic & Admin Data",
              "children": [
                {
                  "name": "gov_pcode",
                  "type": "select_one_from_file province.csv",
                  "labelAr": "المحافظة",
                  "required": true
                },
                {
                  "name": "district_code",
                  "type": "select_one_from_file district.csv",
                  "labelAr": "المديرية",
                  "required": true
                }
              ]
            },
            {
              "name": "group_technical",
              "type": "group",
              "labelAr": "البيانات الفنية والتشغيلية",
              "children": [
                {
                  "name": "water_facility_name",
                  "type": "text",
                  "labelAr": "اسم البئر / المنشأة",
                  "required": true,
                  "constraint": ". != ''"
                },
                {
                  "name": "well_type",
                  "type": "select_one well_types",
                  "labelAr": "نوع البئر",
                  "required": true
                },
                {
                  "name": "depth_calc",
                  "type": "calculate",
                  "calculation": "${'$'}{total_depth} * 1.0"
                },
                {
                  "name": "tracking_id",
                  "type": "hidden",
                  "defaultValue": "static_id_101"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val formDef = parser.parseFormDefinition(json)

        assertEquals("yem_water_wells_saadah", formDef.formId)
        assertEquals("2026-09-05-v6", formDef.version)
        assertEquals("1.0", formDef.schemaVersion)
        assertEquals("استمارة حصر وتوثيق آبار المياه - صعدة", formDef.title)
        assertEquals("concat(${'$'}{water_facility_name}, ' - ', ${'$'}{gov_pcode})", formDef.instanceNameExpression)

        // Root elements: start, end, group_location, group_technical -> 4
        assertEquals(4, formDef.rootElements.size)

        // All elements: start, end, group_location, gov_pcode, district_code, group_technical, water_facility_name, well_type, depth_calc, tracking_id -> 10
        val all = formDef.allElements
        assertEquals(10, all.size)

        // Check index lookup
        val index = formDef.elementIndex
        assertTrue(index.containsKey("start"))
        assertTrue(index.containsKey("end"))
        assertTrue(index.containsKey("group_location"))
        assertTrue(index.containsKey("gov_pcode"))
        assertTrue(index.containsKey("water_facility_name"))
        assertTrue(index.containsKey("depth_calc"))
        assertTrue(index.containsKey("tracking_id"))

        val groupLocation = index["group_location"] as? GroupElement
        assertNotNull(groupLocation)
        assertEquals("البيانات الجغرافية والإدارية", groupLocation?.labelAr)
        assertEquals("Geographic & Admin Data", groupLocation?.labelEn)
        assertEquals(2, groupLocation?.children?.size)
    }

    @Test
    fun test2_ChoiceListsParsingWithArabicAndEnglishLabels() {
        val json = """
        {
          "well_types": [
            { "name": "artesian", "labelAr": "بئر ارتوازي عميق", "labelEn": "Deep Artesian Well", "sortOrder": 1 },
            { "name": "dug", "labelAr": "بئر يدوي سطحي", "labelEn": "Hand-dug Shallow Well", "sortOrder": 2 },
            { "name": "other", "labelAr": "أخرى (حدد)", "labelEn": "Other", "sortOrder": 3 }
          ],
          "pumping_mechanism": [
            { "name": "solar", "labelAr": "طاقة شمسية", "labelEn": "Solar System", "sortOrder": 1 },
            { "name": "diesel", "labelAr": "ديزل", "labelEn": "Diesel Engine", "sortOrder": 2 }
          ]
        }
        """.trimIndent()

        val choiceLists = parser.parseChoiceLists(json)

        assertEquals(2, choiceLists.size)
        assertTrue(choiceLists.containsKey("well_types"))
        assertTrue(choiceLists.containsKey("pumping_mechanism"))

        val wellTypes = choiceLists["well_types"]
        assertNotNull(wellTypes)
        assertEquals("well_types", wellTypes?.listName)
        assertEquals(3, wellTypes?.items?.size)

        val firstItem = wellTypes?.items?.get(0)
        assertEquals("artesian", firstItem?.name)
        assertEquals("بئر ارتوازي عميق", firstItem?.labelAr)
        assertEquals("Deep Artesian Well", firstItem?.labelEn)
        assertEquals(1, firstItem?.sortOrder)

        val itemIndex = wellTypes?.itemsByName
        assertNotNull(itemIndex)
        assertEquals("أخرى (حدد)", itemIndex?.get("other")?.labelAr)
    }

    @Test
    fun test3_AdminTierBindingParsing() {
        val json = """
        {
          "formId": "admin_test",
          "title": "Admin Test",
          "elements": [
            {
              "name": "gov_pcode",
              "type": "select_one_from_file province.csv",
              "labelAr": "المحافظة"
            },
            {
              "name": "district_code",
              "type": "select_one_from_file district.csv",
              "labelAr": "المديرية"
            },
            {
              "name": "uzlah_code",
              "type": "select_one_from_file uzlah.csv",
              "labelAr": "العزلة"
            },
            {
              "name": "village_code",
              "type": "select_one_from_file village.csv",
              "labelAr": "القرية"
            },
            {
              "name": "custom_admin",
              "type": "admin_select",
              "labelAr": "موقع إداري مخصص",
              "adminBinding": {
                "tier": "DISTRICT",
                "parentField": "custom_gov"
              }
            }
          ]
        }
        """.trimIndent()

        val formDef = parser.parseFormDefinition(json)
        val index = formDef.elementIndex

        val gov = index["gov_pcode"] as? QuestionElement
        assertNotNull(gov)
        assertEquals(QuestionDataType.ADMIN_SELECT, gov?.dataType)
        assertEquals(AdminTier.GOVERNORATE, gov?.adminBinding?.tier)
        assertNull(gov?.adminBinding?.parentField)

        val dist = index["district_code"] as? QuestionElement
        assertNotNull(dist)
        assertEquals(QuestionDataType.ADMIN_SELECT, dist?.dataType)
        assertEquals(AdminTier.DISTRICT, dist?.adminBinding?.tier)
        assertEquals("gov_pcode", dist?.adminBinding?.parentField)

        val uzlah = index["uzlah_code"] as? QuestionElement
        assertNotNull(uzlah)
        assertEquals(QuestionDataType.ADMIN_SELECT, uzlah?.dataType)
        assertEquals(AdminTier.UZLAH, uzlah?.adminBinding?.tier)
        assertEquals("district_code", uzlah?.adminBinding?.parentField)

        val village = index["village_code"] as? QuestionElement
        assertNotNull(village)
        assertEquals(QuestionDataType.ADMIN_SELECT, village?.dataType)
        assertEquals(AdminTier.VILLAGE, village?.adminBinding?.tier)
        assertEquals("uzlah_code", village?.adminBinding?.parentField)

        val custom = index["custom_admin"] as? QuestionElement
        assertNotNull(custom)
        assertEquals(QuestionDataType.ADMIN_SELECT, custom?.dataType)
        assertEquals(AdminTier.DISTRICT, custom?.adminBinding?.tier)
        assertEquals("custom_gov", custom?.adminBinding?.parentField)
    }

    @Test
    fun test4_CalculationAndHiddenElementsParsing() {
        val json = """
        {
          "formId": "calc_hidden_test",
          "title": "Calc and Hidden Test",
          "elements": [
            {
              "name": "static_depth",
              "type": "decimal",
              "labelAr": "العمق الثابت"
            },
            {
              "name": "dynamic_depth",
              "type": "decimal",
              "labelAr": "العمق الديناميكي"
            },
            {
              "name": "drawdown_calc",
              "type": "calculate",
              "calculation": "${'$'}{dynamic_depth} - ${'$'}{static_depth}",
              "relevant": "${'$'}{static_depth} > 0 and ${'$'}{dynamic_depth} > 0"
            },
            {
              "name": "app_version_tag",
              "type": "hidden",
              "defaultValue": "v6.0-prod",
              "relevant": "${'$'}{static_depth} != null"
            }
          ]
        }
        """.trimIndent()

        val formDef = parser.parseFormDefinition(json)
        val index = formDef.elementIndex

        val calc = index["drawdown_calc"] as? CalculateElement
        assertNotNull(calc)
        assertEquals("${'$'}{dynamic_depth} - ${'$'}{static_depth}", calc?.calculationExpr?.rawExpression)
        assertEquals(ExpressionContext.CALCULATION, calc?.calculationExpr?.context)
        assertEquals(setOf("dynamic_depth", "static_depth"), calc?.calculationExpr?.referencedFields)
        assertEquals(setOf("static_depth", "dynamic_depth"), calc?.relevantExpr?.referencedFields)

        val hidden = index["app_version_tag"] as? HiddenElement
        assertNotNull(hidden)
        assertEquals("v6.0-prod", hidden?.defaultValueExpr)
        assertEquals(setOf("static_depth"), hidden?.relevantExpr?.referencedFields)
    }

    @Test
    fun test5_SystemTimestampElementsParsing() {
        val json = """
        {
          "formId": "timestamp_test",
          "title": "Timestamp Test",
          "elements": [
            { "name": "start_time", "type": "start" },
            { "name": "survey_date", "type": "date", "labelAr": "تاريخ النزول" },
            { "name": "end_time", "type": "end" }
          ]
        }
        """.trimIndent()

        val formDef = parser.parseFormDefinition(json)
        val index = formDef.elementIndex

        val start = index["start_time"] as? SystemTimestampElement
        assertNotNull(start)
        assertEquals(TimestampType.START, start?.timestampType)

        val end = index["end_time"] as? SystemTimestampElement
        assertNotNull(end)
        assertEquals(TimestampType.END, end?.timestampType)
    }

    @Test
    fun test6_DependencyExtraction() {
        val expressionRelevance = "${'$'}{well_type} = 'other' and ${'$'}{is_active} = 'yes'"
        val exprRelevance = FormDefinitionParser.createExpression(expressionRelevance, ExpressionContext.RELEVANCE)
        assertNotNull(exprRelevance)
        assertEquals(setOf("well_type", "is_active"), exprRelevance?.referencedFields)

        val expressionChoiceFilter = "district_pcode = ${'$'}{gov_pcode} and uzlah_pcode = ${'$'}{district_code}"
        val exprChoiceFilter = FormDefinitionParser.createExpression(expressionChoiceFilter, ExpressionContext.CHOICE_FILTER)
        assertNotNull(exprChoiceFilter)
        assertEquals(setOf("gov_pcode", "district_code"), exprChoiceFilter?.referencedFields)

        val expressionCalc = "concat(${'$'}{prefix}, '_', ${'$'}{facility_code}, '_', ${'$'}{suffix})"
        val exprCalc = FormDefinitionParser.createExpression(expressionCalc, ExpressionContext.CALCULATION)
        assertNotNull(exprCalc)
        assertEquals(setOf("prefix", "facility_code", "suffix"), exprCalc?.referencedFields)
    }

    @Test
    fun test7_SelfReferenceHandlingInConstraint() {
        val json = """
        {
          "formId": "self_ref_test",
          "title": "Self Reference Test",
          "elements": [
            {
              "name": "water_depth",
              "type": "decimal",
              "labelAr": "عمق المياه",
              "constraint": ". > 0 and . <= 1000",
              "constraintMessageAr": "يجب أن يكون العمق أكبر من صفر وأقل من 1000 متر"
            }
          ]
        }
        """.trimIndent()

        val pkgDir = File(tempDir, "self_ref_pkg")
        pkgDir.mkdirs()
        File(pkgDir, "metadata.json").writeText("""{"formId":"self_ref_test","version":"1.0","name":"Self Ref Test"}""")
        File(pkgDir, "form_definition.json").writeText(json)
        File(pkgDir, "choices.json").writeText("{}")

        val result = validator.validatePackage(pkgDir)
        assertTrue("Package with self-referencing '.' constraint must pass validation", result.isValid)
        assertTrue("Dependency graph must not have circular dependency for self-constraint", result.errors.isEmpty())
    }

    @Test
    fun test8_CycleDetectionRejectsCircularDependency() {
        // Direct cycle: var_a depends on var_b, var_b depends on var_a
        val cycleGraph = mapOf(
            "var_a" to setOf("var_b"),
            "var_b" to setOf("var_c"),
            "var_c" to setOf("var_a")
        )

        val cycleErrors = validator.detectCycles(cycleGraph)
        assertFalse("Cycle must be detected", cycleErrors.isEmpty())
        assertTrue("Error must contain INVALID_PACKAGE_STRUCTURE", cycleErrors.any { it.contains("INVALID_PACKAGE_STRUCTURE") })
        assertTrue("Error must contain cycle path", cycleErrors.any { it.contains("var_a -> var_b -> var_c -> var_a") || it.contains("var_b -> var_c -> var_a -> var_b") })

        // Validate package level rejection
        val json = """
        {
          "formId": "cycle_test",
          "title": "Cycle Test",
          "elements": [
            {
              "name": "depth_a",
              "type": "calculate",
              "calculation": "${'$'}{depth_b} + 10"
            },
            {
              "name": "depth_b",
              "type": "calculate",
              "calculation": "${'$'}{depth_a} - 5"
            }
          ]
        }
        """.trimIndent()

        val pkgDir = File(tempDir, "cycle_pkg")
        pkgDir.mkdirs()
        File(pkgDir, "metadata.json").writeText("""{"formId":"cycle_test","version":"1.0","name":"Cycle Test"}""")
        File(pkgDir, "form_definition.json").writeText(json)
        File(pkgDir, "choices.json").writeText("{}")

        val result = validator.validatePackage(pkgDir)
        assertFalse("Package with circular dependency must be rejected", result.isValid)
        assertTrue("Errors must mention INVALID_PACKAGE_STRUCTURE", result.errors.any { it.contains("INVALID_PACKAGE_STRUCTURE") })
    }

    @Test
    fun test9_DuplicateElementNameDetection() {
        val json = """
        {
          "formId": "duplicate_test",
          "title": "Duplicate Test",
          "elements": [
            {
              "name": "water_depth",
              "type": "decimal",
              "labelAr": "العمق الأول"
            },
            {
              "name": "group_extra",
              "type": "group",
              "labelAr": "مجموعة إضافية",
              "children": [
                {
                  "name": "water_depth",
                  "type": "decimal",
                  "labelAr": "العمق المكرر"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val pkgDir = File(tempDir, "duplicate_pkg")
        pkgDir.mkdirs()
        File(pkgDir, "metadata.json").writeText("""{"formId":"duplicate_test","version":"1.0","name":"Duplicate Test"}""")
        File(pkgDir, "form_definition.json").writeText(json)
        File(pkgDir, "choices.json").writeText("{}")

        val result = validator.validatePackage(pkgDir)
        assertFalse("Package with duplicate element names must fail validation", result.isValid)
        assertTrue("Error must mention duplicate element name", result.errors.any { it.contains("water_depth") && it.contains("تكرار") })
    }

    @Test
    fun test10_MissingChoiceListReferenceDetection() {
        val json = """
        {
          "formId": "missing_choice_test",
          "title": "Missing Choice List Test",
          "elements": [
            {
              "name": "pump_status",
              "type": "select_one non_existent_list",
              "labelAr": "حالة المضخة"
            }
          ]
        }
        """.trimIndent()

        val pkgDir = File(tempDir, "missing_choice_pkg")
        pkgDir.mkdirs()
        File(pkgDir, "metadata.json").writeText("""{"formId":"missing_choice_test","version":"1.0","name":"Missing Choice Test"}""")
        File(pkgDir, "form_definition.json").writeText(json)
        File(pkgDir, "choices.json").writeText("""{"valid_list": [{"name":"v1","labelAr":"Option 1"}]}""")

        val result = validator.validatePackage(pkgDir)
        assertFalse("Package referencing non-existent choice list must fail validation", result.isValid)
        assertTrue("Error must mention missing list name", result.errors.any { it.contains("non_existent_list") })
    }

    @Test
    fun test11_EndToEndPackageHydrationFromDirectory() {
        val pkgDir = File(tempDir, "e2e_valid_pkg")
        pkgDir.mkdirs()

        val metadataJson = """
        {
          "formId": "yem_water_springs_saadah",
          "version": "2026-09-05-v6",
          "name": "استمارة حصر وتوثيق عيون المياه - محافظة صعدة",
          "description": "الاستمارة الميدانية المعتمدة لتوثيق الينابيع والعيون المائية",
          "publisher": "وزارة المياه والبيئة - قطاع الموارد المائية",
          "surveyType": "SPRING",
          "targetFacilityType": "SPRING",
          "minAppVersion": "1.0.0",
          "minAppVersionCode": 1,
          "schemaVersion": "1.0",
          "packageVersion": 1,
          "targetMinistry": "وزارة المياه والبيئة - الجمهورية اليمنية",
          "defaultLanguage": "ar",
          "instanceNameExpression": "concat(${'$'}{spring_name}, ' - ', ${'$'}{gov_pcode})"
        }
        """.trimIndent()
        File(pkgDir, "metadata.json").writeText(metadataJson)

        val choicesJson = """
        {
          "spring_type": [
            { "name": "natural", "labelAr": "عين طبيعية متدفقة", "labelEn": "Natural Flowing Spring", "sortOrder": 1 },
            { "name": "protected", "labelAr": "عين محمية بحوض", "labelEn": "Protected Spring", "sortOrder": 2 }
          ]
        }
        """.trimIndent()
        File(pkgDir, "choices.json").writeText(choicesJson)

        val formDefJson = """
        {
          "formId": "yem_water_springs_saadah",
          "version": "2026-09-05-v6",
          "schemaVersion": "1.0",
          "title": "استمارة حصر وتوثيق عيون المياه - محافظة صعدة",
          "defaultLanguage": "ar",
          "elements": [
            { "name": "start", "type": "start" },
            {
              "name": "group_info",
              "type": "group",
              "labelAr": "بيانات العين",
              "children": [
                { "name": "spring_name", "type": "text", "labelAr": "اسم العين المائي", "required": true },
                { "name": "spring_type", "type": "select_one spring_type", "labelAr": "نوع العين", "required": true }
              ]
            },
            { "name": "end", "type": "end" }
          ]
        }
        """.trimIndent()
        File(pkgDir, "form_definition.json").writeText(formDefJson)

        val sequencePoolJson = """
        {
          "provisionedPools": [
            {
              "adminBucketKey": "YE110101",
              "facilityType": "SP",
              "rangeStart": 1,
              "rangeEnd": 100,
              "currentNext": 1
            }
          ]
        }
        """.trimIndent()
        File(pkgDir, "sequence_pool.json").writeText(sequencePoolJson)

        // Validation
        val validation = validator.validatePackage(pkgDir)
        assertTrue("Validation must succeed for valid package", validation.isValid)
        assertEquals(0, validation.errors.size)
        assertNotNull(validation.metadata)
        assertNotNull(validation.formDefinition)
        assertEquals(1, validation.choiceLists.size)

        // Hydration
        val hydratedPackage = parser.hydratePackageFromDirectory(
            packageDir = pkgDir,
            isActive = true,
            installationDate = "2026-09-05 20:00:00",
            checksum = validation.calculatedChecksum
        )

        assertEquals("yem_water_springs_saadah", hydratedPackage.formId)
        assertEquals("2026-09-05-v6", hydratedPackage.version)
        assertEquals("استمارة حصر وتوثيق عيون المياه - محافظة صعدة", hydratedPackage.name)
        assertEquals("SPRING", hydratedPackage.targetFacilityType)
        assertTrue(hydratedPackage.isActive)
        assertEquals(validation.calculatedChecksum, hydratedPackage.checksum)

        // Verify hydrated FormDefinition
        val def = hydratedPackage.formDefinition
        assertNotNull(def)
        assertEquals(3, def?.rootElements?.size)
        assertEquals(5, def?.allElements?.size) // start + group_info + spring_name + spring_type + end

        // Verify hydrated ChoiceLists
        assertEquals(1, hydratedPackage.choiceLists.size)
        assertTrue(hydratedPackage.choiceLists.containsKey("spring_type"))

        // Verify hydrated SequenceRanges
        val ranges = hydratedPackage.sequencePoolRanges
        assertNotNull(ranges)
        assertEquals(1, ranges?.size)
        assertEquals("YE110101", ranges?.get(0)?.adminBucketKey)
        assertEquals("SP", ranges?.get(0)?.facilityType)
        assertEquals(1, ranges?.get(0)?.rangeStart)
        assertEquals(100, ranges?.get(0)?.rangeEnd)
    }
}
