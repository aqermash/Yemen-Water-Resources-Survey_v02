package com.yemen.watersurvey.domain.model

import java.io.File

/**
 * Domain model representing a versioned survey form package in the Yemen Water Survey System.
 */
data class FormPackage(
    val formId: String,
    val version: String,
    val name: String,
    val description: String,
    val publisher: String,
    val checksum: String,
    val installationDate: String,
    val isActive: Boolean = false,
    val packagePath: String,
    val hasFormDefinition: Boolean = true,
    val hasChoices: Boolean = true,
    val hasPdfTemplate: Boolean = false,
    val hasPdfMapping: Boolean = false,
    val metadataExtra: Map<String, String> = emptyMap()
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
    val description: String,
    val publisher: String,
    val surveyType: String = "WELL",
    val minAppVersion: String = "1.0.0",
    val targetMinistry: String = "وزارة المياه والبيئة - الجمهورية اليمنية",
    val extraProperties: Map<String, String> = emptyMap()
)

/**
 * Result of validating a form package directory or zip
 */
data class PackageValidationResult(
    val isValid: Boolean,
    val metadata: PackageMetadata? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val checkedFiles: List<String> = emptyList(),
    val calculatedChecksum: String = ""
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
