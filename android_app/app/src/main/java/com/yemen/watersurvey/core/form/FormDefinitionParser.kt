package com.yemen.watersurvey.core.form

import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * High-performance, offline-first parser for Survey Form Packages.
 *
 * Implements Phase 12A specification under C6.1.2:
 * - Parses metadata.json into PackageMetadata
 * - Parses choices.json into Map<String, ChoiceList>
 * - Parses form_definition.json into hierarchical FormDefinition
 * - Parses sequence_pool.json into List<SequenceRange>
 * - Extracts variable dependencies from expressions with context-self isolation
 * - Hydrates complete FormPackage domain model
 */
class FormDefinitionParser {

    companion object {
        private val VARIABLE_PATTERN = Regex("""\$\{([a-zA-Z0-9_]+)\}""")

        /**
         * Extracts variable names referenced inside an expression string (${var_name}).
         * Excludes context-self references ('.').
         */
        fun extractReferencedFields(rawExpression: String?): Set<String> {
            if (rawExpression.isNullOrBlank()) return emptySet()
            return VARIABLE_PATTERN.findAll(rawExpression)
                .map { it.groupValues[1] }
                .toSet()
        }

        /**
         * Creates a FormExpression instance from a raw expression string and context.
         */
        fun createExpression(rawExpression: String?, context: ExpressionContext): FormExpression? {
            if (rawExpression.isNullOrBlank()) return null
            val trimmed = rawExpression.trim()
            val refs = extractReferencedFields(trimmed)
            return FormExpression(
                rawExpression = trimmed,
                context = context,
                referencedFields = refs
            )
        }
    }

    /**
     * Parses metadata.json content into PackageMetadata.
     */
    fun parseMetadata(jsonString: String): PackageMetadata {
        val json = JSONObject(jsonString)
        val formId = json.optString("formId", json.optString("form_id", "")).trim()
        val version = json.optString("version", json.optString("formVersion", json.optString("form_version", ""))).trim()
        val name = json.optString("name", json.optString("form_title", json.optString("title", ""))).trim()
        val description = json.optString("description", "").trim()
        val publisher = json.optString("publisher", "وزارة المياه والبيئة - اليمن").trim()
        val surveyType = json.optString("surveyType", json.optString("survey_type", "WELL")).trim()
        val targetFacilityType = json.optString("targetFacilityType", json.optString("target_facility_type", surveyType)).trim()
        val minAppVersion = json.optString("minAppVersion", json.optString("min_app_version", "1.0.0")).trim()
        val minAppVersionCode = json.optInt("minAppVersionCode", json.optInt("min_app_version_code", 1))
        val schemaVersion = json.optString("schemaVersion", json.optString("schema_version", "1.0")).trim()
        val packageVersion = json.optInt("packageVersion", json.optInt("package_version", 1))
        val targetMinistry = json.optString("targetMinistry", json.optString("target_ministry", "وزارة المياه والبيئة - الجمهورية اليمنية")).trim()
        val defaultLanguage = json.optString("defaultLanguage", json.optString("default_language", "ar")).trim()
        val instanceNameExpr = json.optString("instanceNameExpression", json.optString("instance_name", "")).trim().ifBlank { null }

        val standardKeys = setOf(
            "formId", "form_id", "version", "formVersion", "form_version",
            "name", "form_title", "title", "description", "publisher",
            "surveyType", "survey_type", "targetFacilityType", "target_facility_type",
            "minAppVersion", "min_app_version", "minAppVersionCode", "min_app_version_code",
            "schemaVersion", "schema_version", "packageVersion", "package_version",
            "targetMinistry", "target_ministry", "defaultLanguage", "default_language",
            "instanceNameExpression", "instance_name"
        )

        val extra = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in standardKeys) {
                extra[k] = json.optString(k, "")
            }
        }

        return PackageMetadata(
            formId = formId,
            version = version,
            name = name,
            description = description,
            publisher = publisher,
            surveyType = surveyType,
            targetFacilityType = targetFacilityType,
            minAppVersion = minAppVersion,
            minAppVersionCode = minAppVersionCode,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            targetMinistry = targetMinistry,
            defaultLanguage = defaultLanguage,
            instanceNameExpression = instanceNameExpr,
            extraProperties = extra
        )
    }

    /**
     * Parses choices.json content into a Map of listName -> ChoiceList.
     * Supports:
     * - Object map format: { "listName": [ { "name": "...", "labelAr": "..." } ] }
     * - Array of list objects format: [ { "listName": "...", "items": [ ... ] } ]
     * - Flat array format: [ { "list_name": "...", "name": "...", "label": "..." } ]
     */
    fun parseChoiceLists(jsonString: String): Map<String, ChoiceList> {
        val trimmed = jsonString.trim()
        val result = mutableMapOf<String, MutableList<ChoiceItem>>()

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val keys = root.keys()
            while (keys.hasNext()) {
                val listName = keys.next()
                val array = root.optJSONArray(listName) ?: continue
                val items = parseChoiceItemsArray(array)
                result[listName] = items
            }
        } else if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val itemObj = array.optJSONObject(i) ?: continue
                if (itemObj.has("listName") && itemObj.has("items")) {
                    val listName = itemObj.getString("listName")
                    val itemsArray = itemObj.optJSONArray("items") ?: JSONArray()
                    result[listName] = parseChoiceItemsArray(itemsArray)
                } else if (itemObj.has("list_name") || itemObj.has("listName")) {
                    val listName = itemObj.optString("list_name", itemObj.optString("listName", ""))
                    if (listName.isNotBlank()) {
                        val choiceItem = parseSingleChoiceItem(itemObj, result[listName]?.size ?: 0)
                        val list = result.getOrPut(listName) { mutableListOf() }
                        list.add(choiceItem)
                    }
                }
            }
        }

        return result.mapValues { (name, items) ->
            ChoiceList(listName = name, items = items)
        }
    }

    private fun parseChoiceItemsArray(array: JSONArray): MutableList<ChoiceItem> {
        val items = mutableListOf<ChoiceItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items.add(parseSingleChoiceItem(obj, i))
        }
        return items
    }

    private fun parseSingleChoiceItem(obj: JSONObject, index: Int): ChoiceItem {
        val name = obj.optString("name", "").trim()
        val labelAr = obj.optString("labelAr", obj.optString("label", obj.optString("label::Arabic", obj.optString("label_ar", "")))).trim()
        val labelEn = obj.optString("labelEn", obj.optString("label::English", obj.optString("label_en", ""))).trim().ifBlank { null }
        val sortOrder = obj.optInt("sortOrder", obj.optInt("sort_order", index + 1))
        return ChoiceItem(
            name = name,
            labelAr = labelAr,
            labelEn = labelEn,
            sortOrder = sortOrder
        )
    }

    /**
     * Parses form_definition.json content into FormDefinition.
     */
    fun parseFormDefinition(jsonString: String): FormDefinition {
        val json = JSONObject(jsonString)
        val formId = json.optString("formId", json.optString("form_id", "")).trim()
        val version = json.optString("version", json.optString("formVersion", json.optString("form_version", ""))).trim()
        val schemaVersion = json.optString("schemaVersion", json.optString("schema_version", "1.0")).trim()
        val title = json.optString("title", json.optString("form_title", json.optString("name", ""))).trim()
        val defaultLanguage = json.optString("defaultLanguage", json.optString("default_language", "ar")).trim()
        val instanceNameExpr = json.optString("instanceNameExpression", json.optString("instance_name", "")).trim().ifBlank { null }

        val rawElements = json.optJSONArray("elements")
            ?: json.optJSONArray("questions")
            ?: json.optJSONArray("fields")
            ?: json.optJSONArray("rootElements")
            ?: JSONArray()

        val rootElements = parseElementsArray(rawElements)

        return FormDefinition(
            formId = formId,
            version = version,
            schemaVersion = schemaVersion,
            title = title,
            defaultLanguage = defaultLanguage,
            instanceNameExpression = instanceNameExpr,
            rootElements = rootElements
        )
    }

    private fun parseElementsArray(array: JSONArray): List<FormElement> {
        val elements = mutableListOf<FormElement>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val elem = parseFormElement(obj)
            if (elem != null) {
                elements.add(elem)
            }
        }
        return elements
    }

    private fun parseFormElement(obj: JSONObject): FormElement? {
        val name = obj.optString("name", "").trim()
        val rawType = obj.optString("type", obj.optString("elementType", obj.optString("dataType", ""))).trim()
        val normalizedType = rawType.lowercase()

        val relevantExprStr = obj.optString("relevantExpression", obj.optString("relevantExpr", obj.optString("relevant", ""))).trim().ifBlank { null }
        val relevantExpr = createExpression(relevantExprStr, ExpressionContext.RELEVANCE)

        // 1. Group Element
        if (normalizedType == "group" || normalizedType == "begin_group" || obj.has("children")) {
            val labelAr = obj.optString("labelAr", obj.optString("label", obj.optString("label::Arabic", obj.optString("label_ar", "")))).trim()
            val labelEn = obj.optString("labelEn", obj.optString("label::English", obj.optString("label_en", ""))).trim().ifBlank { null }
            val childrenArray = obj.optJSONArray("children") ?: JSONArray()
            val children = parseElementsArray(childrenArray)
            return GroupElement(
                name = name,
                labelAr = labelAr,
                labelEn = labelEn,
                relevantExpr = relevantExpr,
                children = children
            )
        }

        // 2. System Timestamp Elements (start / end)
        if (normalizedType == "start") {
            return SystemTimestampElement(
                name = name.ifBlank { "start" },
                timestampType = TimestampType.START,
                relevantExpr = relevantExpr
            )
        }
        if (normalizedType == "end") {
            return SystemTimestampElement(
                name = name.ifBlank { "end" },
                timestampType = TimestampType.END,
                relevantExpr = relevantExpr
            )
        }

        // 3. Calculate Element
        if (normalizedType == "calculate") {
            val calcExprStr = obj.optString("calculationExpression", obj.optString("calculationExpr", obj.optString("calculation", ""))).trim()
            val calcExpr = createExpression(calcExprStr, ExpressionContext.CALCULATION)
                ?: FormExpression(rawExpression = "", context = ExpressionContext.CALCULATION)
            return CalculateElement(
                name = name,
                calculationExpr = calcExpr,
                relevantExpr = relevantExpr
            )
        }

        // 4. Hidden Element
        if (normalizedType == "hidden") {
            val defaultValue = obj.optString("defaultValue", obj.optString("defaultValueExpr", obj.optString("default", ""))).trim().ifBlank { null }
            return HiddenElement(
                name = name,
                defaultValueExpr = defaultValue,
                relevantExpr = relevantExpr
            )
        }

        // 5. Question Element
        val labelAr = obj.optString("labelAr", obj.optString("label", obj.optString("label::Arabic", obj.optString("label_ar", "")))).trim()
        val labelEn = obj.optString("labelEn", obj.optString("label::English", obj.optString("label_en", ""))).trim().ifBlank { null }
        val hintAr = obj.optString("hintAr", obj.optString("hint", obj.optString("hint::Arabic", obj.optString("hint_ar", "")))).trim().ifBlank { null }
        val isRequired = obj.optBoolean("isRequired", obj.optBoolean("required", false))

        val constraintExprStr = obj.optString("constraintExpression", obj.optString("constraintExpr", obj.optString("constraint", ""))).trim().ifBlank { null }
        val constraintExpr = createExpression(constraintExprStr, ExpressionContext.CONSTRAINT)
        val constraintMessageAr = obj.optString("constraintMessageAr", obj.optString("constraint_message", obj.optString("constraintMessage", ""))).trim().ifBlank { null }

        val choiceFilterExprStr = obj.optString("choiceFilterExpression", obj.optString("choiceFilterExpr", obj.optString("choice_filter", obj.optString("choiceFilter", "")))).trim().ifBlank { null }
        val choiceFilterExpr = createExpression(choiceFilterExprStr, ExpressionContext.CHOICE_FILTER)

        val appearance = obj.optString("appearance", "").trim().ifBlank { null }

        // Parse QuestionDataType & Choice List / Admin Binding
        var choicesListName = obj.optString("choicesListName", obj.optString("choicesList", obj.optString("choiceList", obj.optString("choice_list", "")))).trim().ifBlank { null }
        var adminBinding: AdminTierBinding? = null

        if (obj.has("adminBinding")) {
            val adminObj = obj.getJSONObject("adminBinding")
            val tierStr = adminObj.optString("tier", "").uppercase().trim()
            val parentField = adminObj.optString("parentField", "").trim().ifBlank { null }
            val tier = try {
                AdminTier.valueOf(tierStr)
            } catch (e: Exception) {
                AdminTier.GOVERNORATE
            }
            adminBinding = AdminTierBinding(tier = tier, parentField = parentField)
        }

        val typeParts = rawType.split(Regex("\\s+"), 2)
        val baseTypeStr = typeParts[0].lowercase()

        val parsedDataType: QuestionDataType = when {
            baseTypeStr == "select_one_from_file" || baseTypeStr == "admin_select" || adminBinding != null -> {
                if (adminBinding == null && typeParts.size > 1) {
                    val file = typeParts[1].lowercase()
                    adminBinding = resolveAdminBindingFromFile(file, name)
                } else if (adminBinding == null && obj.has("externalFile")) {
                    val file = obj.getString("externalFile").lowercase()
                    adminBinding = resolveAdminBindingFromFile(file, name)
                }
                QuestionDataType.ADMIN_SELECT
            }
            baseTypeStr == "select_one" -> {
                if (choicesListName == null && typeParts.size > 1) {
                    choicesListName = typeParts[1]
                }
                QuestionDataType.SELECT_ONE
            }
            baseTypeStr == "select_multiple" -> {
                if (choicesListName == null && typeParts.size > 1) {
                    choicesListName = typeParts[1]
                }
                QuestionDataType.SELECT_MULTIPLE
            }
            else -> {
                QuestionDataType.fromString(baseTypeStr) ?: QuestionDataType.TEXT
            }
        }

        return QuestionElement(
            name = name,
            dataType = parsedDataType,
            labelAr = labelAr,
            labelEn = labelEn,
            hintAr = hintAr,
            isRequired = isRequired,
            constraintExpr = constraintExpr,
            constraintMessageAr = constraintMessageAr,
            relevantExpr = relevantExpr,
            choicesListName = choicesListName,
            choiceFilterExpr = choiceFilterExpr,
            adminBinding = adminBinding,
            appearance = appearance
        )
    }

    private fun resolveAdminBindingFromFile(fileName: String, fieldName: String): AdminTierBinding {
        val lower = (fileName + "_" + fieldName).lowercase()
        return when {
            lower.contains("province") || lower.contains("gov") -> AdminTierBinding(AdminTier.GOVERNORATE, null)
            lower.contains("district") -> AdminTierBinding(AdminTier.DISTRICT, "gov_pcode")
            lower.contains("uzlah") -> AdminTierBinding(AdminTier.UZLAH, "district_code")
            lower.contains("village") -> AdminTierBinding(AdminTier.VILLAGE, "uzlah_code")
            else -> AdminTierBinding(AdminTier.GOVERNORATE, null)
        }
    }

    /**
     * Parses sequence_pool.json content into List<SequenceRange>.
     */
    fun parseSequencePool(jsonString: String): List<SequenceRange> {
        val trimmed = jsonString.trim()
        val result = mutableListOf<SequenceRange>()

        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val array = json.optJSONArray("provisionedPools")
                ?: json.optJSONArray("pools")
                ?: json.optJSONArray("ranges")
                ?: JSONArray()
            result.addAll(parseSequenceRangesArray(array))
        } else if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            result.addAll(parseSequenceRangesArray(array))
        }

        return result
    }

    private fun parseSequenceRangesArray(array: JSONArray): List<SequenceRange> {
        val list = mutableListOf<SequenceRange>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val adminBucketKey = obj.optString("adminBucketKey", obj.optString("bucket_key", "")).trim()
            val facilityType = obj.optString("facilityType", obj.optString("facility_type", "WL")).trim()
            val rangeStart = obj.optInt("rangeStart", obj.optInt("range_start", 1))
            val rangeEnd = obj.optInt("rangeEnd", obj.optInt("range_end", 100))
            val currentNext = obj.optInt("currentNext", obj.optInt("current_next", rangeStart))
            if (adminBucketKey.isNotBlank()) {
                list.add(
                    SequenceRange(
                        adminBucketKey = adminBucketKey,
                        facilityType = facilityType,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        currentNext = currentNext
                    )
                )
            }
        }
        return list
    }

    /**
     * Hydrates a complete FormPackage from an installed or staged package directory.
     */
    fun hydratePackageFromDirectory(
        packageDir: File,
        isActive: Boolean = false,
        installationDate: String = "",
        checksum: String = ""
    ): FormPackage {
        val metadataFile = File(packageDir, FormPackageManager.REQUIRED_METADATA_FILE)
        val formDefFile = File(packageDir, FormPackageManager.REQUIRED_FORM_DEF_FILE)
        val choicesFile = File(packageDir, FormPackageManager.REQUIRED_CHOICES_FILE)
        val sequencePoolFile = File(packageDir, FormPackageManager.OPTIONAL_SEQUENCE_POOL)
        val pdfTemplateFile = File(packageDir, FormPackageManager.OPTIONAL_TEMPLATE_PDF)
        val pdfMappingFile = File(packageDir, FormPackageManager.OPTIONAL_PDF_MAPPING)

        val metadata = if (metadataFile.exists()) parseMetadata(metadataFile.readText()) else null
        val formDef = if (formDefFile.exists()) parseFormDefinition(formDefFile.readText()) else null
        val choiceLists = if (choicesFile.exists()) parseChoiceLists(choicesFile.readText()) else emptyMap()
        val sequenceRanges = if (sequencePoolFile.exists()) parseSequencePool(sequencePoolFile.readText()) else null

        val formId = metadata?.formId ?: formDef?.formId ?: packageDir.parentFile?.name ?: "unknown_form"
        val version = metadata?.version ?: formDef?.version ?: packageDir.name ?: "1.0"
        val name = metadata?.name ?: formDef?.title ?: formId

        return FormPackage(
            formId = formId,
            version = version,
            name = name,
            description = metadata?.description ?: "",
            publisher = metadata?.publisher ?: "وزارة المياه والبيئة - اليمن",
            checksum = checksum,
            installationDate = installationDate,
            isActive = isActive,
            packagePath = packageDir.absolutePath,
            hasFormDefinition = formDefFile.exists(),
            hasChoices = choicesFile.exists(),
            hasPdfTemplate = pdfTemplateFile.exists(),
            hasPdfMapping = pdfMappingFile.exists(),
            metadataExtra = metadata?.extraProperties ?: emptyMap(),
            schemaVersion = metadata?.schemaVersion ?: formDef?.schemaVersion ?: "1.0",
            packageVersion = metadata?.packageVersion ?: 1,
            targetFacilityType = metadata?.targetFacilityType ?: "WELL",
            defaultLanguage = metadata?.defaultLanguage ?: formDef?.defaultLanguage ?: "ar",
            instanceNameExpr = metadata?.instanceNameExpression ?: formDef?.instanceNameExpression,
            formDefinition = formDef,
            choiceLists = choiceLists,
            sequencePoolRanges = sequenceRanges
        )
    }
}
