package com.yemen.watersurvey.core.pdf

import java.io.File

enum class PdfTextAlign {
    LEFT,
    CENTER,
    RIGHT
}

data class PdfFieldCoordinate(
    val key: String,
    val labelAr: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 10f,
    val isBold: Boolean = false,
    val align: PdfTextAlign = PdfTextAlign.RIGHT,
    val colorHex: String = "#0F172A",
    val maxWidth: Float? = null
)

data class PdfTemplateMapping(
    val surveyType: String,
    val templateTitleAr: String,
    val pageWidthPt: Float = 595.28f, // Standard ISO A4 Width (72 dpi)
    val pageHeightPt: Float = 841.89f, // Standard ISO A4 Height (72 dpi)
    val totalPages: Int = 1,
    val fields: List<PdfFieldCoordinate>
)

data class PdfExportResult(
    val file: File,
    val fileName: String,
    val relativePath: String,
    val recordId: String,
    val surveyType: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val generatedAt: String,
    val isOfflineGenerated: Boolean = true
)

/**
 * Encapsulates a downloadable / dynamic survey form template package.
 * Allows decoupling PDF generation from hardcoded APK assets.
 */
data class PdfTemplatePackage(
    val formId: String,
    val formVersion: String = "1.0",
    val pdfTemplateFile: File? = null,
    val mappingFile: File? = null,
    val mapping: PdfTemplateMapping? = null
)
