package com.yemen.watersurvey.domain.model

import java.io.File

/**
 * Domain model representing a versioned survey form package in the Yemen Water Survey System.
 */
data class FormPackage(
    val formId: String,
    val version: String,
    val name: String,
    val description: String = "",
    val publisher: String = "",
    val checksum: String = "",
    val installationDate: String = "",
    val isActive: Boolean = false,
    val packagePath: String = "",
    val hasFormDefinition: Boolean = true,
    val hasChoices: Boolean = true,
    val hasPdfTemplate: Boolean = false,
    val hasPdfMapping: Boolean = false,
    val metadataExtra: Map<String, String> = emptyMap(),
    val schemaVersion: String = "1.0",
    val packageVersion: Int = 1,
    val targetFacilityType: String = "GENERIC",
    val defaultLanguage: String = "ar",
    val instanceNameExpr: String? = null,
    val formDefinition: FormDefinition? = null,
    val choiceLists: Map<String, ChoiceList> = emptyMap(),
    val sequencePoolRanges: List<SequenceRange>? = null
) {
    val compositeId: String get() = "${formId}@${version}"
}

/**
 * Metadata extracted from package metadata.json
 */
data class PackageMetadata(
    val formId: String,
    val version: String,
    val name: String,
    val description: String = "",
    val publisher: String = "",
    val surveyType: String = "WELL",
    val targetFacilityType: String = surveyType,
    val minAppVersion: String = "1.0.0",
    val minAppVersionCode: Int = 1,
    val schemaVersion: String = "1.0",
    val packageVersion: Int = 1,
    val targetMinistry: String = "وزارة المياه والبيئة - الجمهورية اليمنية",
    val defaultLanguage: String = "ar",
    val instanceNameExpression: String? = null,
    val extraProperties: Map<String, String> = emptyMap()
)

/**
 * Result of validating a form package directory or zip
 */
data class PackageValidationResult(
    val isValid: Boolean,
    val metadata: PackageMetadata? = null,
    val formDefinition: FormDefinition? = null,
    val choiceLists: Map<String, ChoiceList> = emptyMap(),
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val checkedFiles: List<String> = emptyList(),
    val calculatedChecksum: String = "",
    val dependencyGraph: Map<String, Set<String>> = emptyMap()
)

/**
 * Result of importing a form package
 */
sealed class PackageImportResult {
    data class Success(
        val formPackage: FormPackage,
        val message: String
    ) : PackageImportResult()

    data class Failure(
        val errors: List<String>,
        val stage: String
    ) : PackageImportResult()
}

/**
 * Canonical normalized representation of a survey questionnaire form definition.
 */
data class FormDefinition(
    val formId: String,
    val version: String,
    val schemaVersion: String = "1.0",
    val title: String,
    val defaultLanguage: String = "ar",
    val instanceNameExpression: String? = null,
    val rootElements: List<FormElement> = emptyList()
) {
    /**
     * Flattens and returns all elements across the tree (root elements and recursive group children).
     */
    val allElements: List<FormElement> by lazy {
        fun collect(elements: List<FormElement>): List<FormElement> {
            val list = mutableListOf<FormElement>()
            for (elem in elements) {
                list.add(elem)
                if (elem is GroupElement) {
                    list.addAll(collect(elem.children))
                }
            }
            return list
        }
        collect(rootElements)
    }

    /**
     * Fast O(1) lookup map indexing all elements by name.
     */
    val elementIndex: Map<String, FormElement> by lazy {
        allElements.associateBy { it.name }
    }
}

/**
 * Base sealed interface for all form elements in the questionnaire tree.
 */
sealed interface FormElement {
    val name: String
    val relevantExpr: FormExpression?
}

/**
 * Visual section/container group element containing ordered child elements.
 */
data class GroupElement(
    override val name: String,
    val labelAr: String,
    val labelEn: String? = null,
    override val relevantExpr: FormExpression? = null,
    val children: List<FormElement> = emptyList()
) : FormElement

/**
 * Interactive user-input question element.
 */
data class QuestionElement(
    override val name: String,
    val dataType: QuestionDataType,
    val labelAr: String,
    val labelEn: String? = null,
    val hintAr: String? = null,
    val isRequired: Boolean = false,
    val constraintExpr: FormExpression? = null,
    val constraintMessageAr: String? = null,
    override val relevantExpr: FormExpression? = null,
    val choicesListName: String? = null,
    val choiceFilterExpr: FormExpression? = null,
    val adminBinding: AdminTierBinding? = null,
    val appearance: String? = null
) : FormElement

/**
 * Dynamic in-memory calculated field.
 */
data class CalculateElement(
    override val name: String,
    val calculationExpr: FormExpression,
    override val relevantExpr: FormExpression? = null
) : FormElement

/**
 * Hidden system parameter or static configuration tag.
 */
data class HiddenElement(
    override val name: String,
    val defaultValueExpr: String? = null,
    override val relevantExpr: FormExpression? = null
) : FormElement

/**
 * Non-visual session audit timestamp capture element (start or end).
 */
data class SystemTimestampElement(
    override val name: String,
    val timestampType: TimestampType,
    override val relevantExpr: FormExpression? = null
) : FormElement

enum class TimestampType {
    START,
    END
}

/**
 * Supported normalized runtime question data types.
 */
enum class QuestionDataType {
    TEXT,
    INTEGER,
    DECIMAL,
    DATE,
    GEOPOINT,
    IMAGE,
    SELECT_ONE,
    SELECT_MULTIPLE,
    ADMIN_SELECT;

    companion object {
        fun fromString(typeString: String): QuestionDataType? {
            val normalized = typeString.trim().lowercase()
            return when {
                normalized == "text" || normalized == "string" -> TEXT
                normalized == "integer" || normalized == "int" -> INTEGER
                normalized == "decimal" || normalized == "double" || normalized == "float" -> DECIMAL
                normalized == "date" -> DATE
                normalized == "geopoint" || normalized == "location" -> GEOPOINT
                normalized == "image" || normalized == "photo" -> IMAGE
                normalized.startsWith("select_one_from_file") || normalized == "admin_select" -> ADMIN_SELECT
                normalized.startsWith("select_one") -> SELECT_ONE
                normalized.startsWith("select_multiple") -> SELECT_MULTIPLE
                else -> null
            }
        }
    }
}

/**
 * Declarative administrative tier binding linking a question to the independent Administrative Reference Framework.
 */
data class AdminTierBinding(
    val tier: AdminTier,
    val parentField: String? = null
)

enum class AdminTier {
    GOVERNORATE,
    DISTRICT,
    UZLAH,
    VILLAGE
}

/**
 * Static choice list definition containing ordered choice items.
 */
data class ChoiceList(
    val listName: String,
    val items: List<ChoiceItem> = emptyList()
) {
    val itemsByName: Map<String, ChoiceItem> by lazy {
        items.associateBy { it.name }
    }
}

/**
 * Individual option item within a choice list.
 */
data class ChoiceItem(
    val name: String,
    val labelAr: String,
    val labelEn: String? = null,
    val sortOrder: Int = 0
)

/**
 * Declarative expression metadata preserving raw expression text and variable dependencies.
 */
data class FormExpression(
    val rawExpression: String,
    val context: ExpressionContext,
    val referencedFields: Set<String> = emptySet()
)

enum class ExpressionContext {
    RELEVANCE,
    CONSTRAINT,
    CALCULATION,
    CHOICE_FILTER
}

/**
 * Preallocated offline sequence allocation range.
 */
data class SequenceRange(
    val adminBucketKey: String,
    val facilityType: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val currentNext: Int = rangeStart
)

/**
 * Sealed hierarchy of immutable runtime value types.
 */
sealed interface RuntimeValue {
    data class Text(val value: String) : RuntimeValue
    data class Integer(val value: Long) : RuntimeValue
    data class Decimal(val value: Double) : RuntimeValue
    data class Date(val isoDate: String) : RuntimeValue
    data class Choice(val selectedName: String) : RuntimeValue
    data class MultipleChoice(val selectedNames: Set<String>) : RuntimeValue
    data class AdminSelection(val pcode: String, val tier: AdminTier) : RuntimeValue
    data class Geopoint(val latitude: Double, val longitude: Double, val altitudeM: Double, val accuracyM: Float) : RuntimeValue
    data class ImageRef(val attachmentId: String, val sha256: String) : RuntimeValue
}
