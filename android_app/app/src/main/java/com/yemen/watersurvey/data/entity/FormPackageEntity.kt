package com.yemen.watersurvey.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yemen.watersurvey.domain.model.FormPackage

/**
 * Room Database Entity representing an installed Survey Form Package.
 * Preserves strict isolation and does not modify existing survey data tables.
 */
@Entity(
    tableName = "form_packages",
    indices = [
        Index(value = ["form_id", "version"], unique = true),
        Index(value = ["form_id", "is_active"])
    ]
)
data class FormPackageEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_uid")
    val packageUid: String, // format: "formId@version"

    @ColumnInfo(name = "form_id")
    val formId: String,

    @ColumnInfo(name = "version")
    val version: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "publisher")
    val publisher: String,

    @ColumnInfo(name = "package_path")
    val packagePath: String,

    @ColumnInfo(name = "checksum")
    val checksum: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "has_form_definition")
    val hasFormDefinition: Boolean = true,

    @ColumnInfo(name = "has_choices")
    val hasChoices: Boolean = true,

    @ColumnInfo(name = "has_pdf_template")
    val hasPdfTemplate: Boolean = false,

    @ColumnInfo(name = "has_pdf_mapping")
    val hasPdfMapping: Boolean = false,

    @ColumnInfo(name = "installation_date")
    val installationDate: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
) {
    fun toDomainModel(): FormPackage {
        return FormPackage(
            formId = formId,
            version = version,
            name = name,
            description = description,
            publisher = publisher,
            checksum = checksum,
            installationDate = installationDate,
            isActive = isActive,
            packagePath = packagePath,
            hasFormDefinition = hasFormDefinition,
            hasChoices = hasChoices,
            hasPdfTemplate = hasPdfTemplate,
            hasPdfMapping = hasPdfMapping
        )
    }

    companion object {
        fun fromDomainModel(domain: FormPackage, updatedAt: String): FormPackageEntity {
            return FormPackageEntity(
                packageUid = domain.compositeId,
                formId = domain.formId,
                version = domain.version,
                name = domain.name,
                description = domain.description,
                publisher = domain.publisher,
                packagePath = domain.packagePath,
                checksum = domain.checksum,
                isActive = domain.isActive,
                hasFormDefinition = domain.hasFormDefinition,
                hasChoices = domain.hasChoices,
                hasPdfTemplate = domain.hasPdfTemplate,
                hasPdfMapping = domain.hasPdfMapping,
                installationDate = domain.installationDate,
                updatedAt = updatedAt
            )
        }
    }
}
