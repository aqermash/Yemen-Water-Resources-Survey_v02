package com.yemen.watersurvey.core.excel

import android.content.Context
import com.yemen.watersurvey.domain.model.SurveyRecord
import com.yemen.watersurvey.domain.model.SurveyType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val file: File,
    val fileName: String,
    val relativePath: String,
    val recordsCount: Int,
    val generatedAt: String,
    val isOoxmlZip: Boolean = true
)

/**
 * Native Genuine OOXML (.xlsx) Excel Exporter for Yemen Water Survey.
 * Generates valid ZIP-compressed Office Open XML workbooks containing 4 worksheets:
 * - Worksheet 1: Wells (آبار المياه)
 * - Worksheet 2: Springs (العيون والينابيع)
 * - Worksheet 3: Dams (السدود والحواجز)
 * - Worksheet 4: Survey Log (سجل العمليات الميدانية)
 *
 * Keeps Room database 100% read-only and operates completely offline without network calls.
 */
class ExcelExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Generates a genuine OOXML .xlsx ZIP package containing all 4 required worksheets.
     */
    fun exportSurveysToExcel(
        records: List<SurveyRecord>,
        generatedByUsername: String = "system_export"
    ): ExportResult {
        val exportsDir = File(context.filesDir, "exports/excel")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Yemen_Water_Survey_Export_$timestampStr.xlsx"
        val targetFile = File(exportsDir, fileName)

        val wells = records.filter { it.surveyType == SurveyType.WELL }
        val springs = records.filter { it.surveyType == SurveyType.SPRING }
        val dams = records.filter { it.surveyType == SurveyType.DAM }

        // Build genuine ZIP OOXML container
        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                // 2. _rels/.rels
                addZipEntry(zos, "_rels/.rels", buildRootRelsXml())

                // 3. xl/workbook.xml
                addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml())

                // 4. xl/_rels/workbook.xml.rels
                addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

                // 5. xl/styles.xml
                addZipEntry(zos, "xl/styles.xml", buildStylesXml())

                // 6. xl/worksheets/sheet1.xml (Wells)
                addZipEntry(zos, "xl/worksheets/sheet1.xml", buildWellsSheetXml(wells))

                // 7. xl/worksheets/sheet2.xml (Springs)
                addZipEntry(zos, "xl/worksheets/sheet2.xml", buildSpringsSheetXml(springs))

                // 8. xl/worksheets/sheet3.xml (Dams)
                addZipEntry(zos, "xl/worksheets/sheet3.xml", buildDamsSheetXml(dams))

                // 9. xl/worksheets/sheet4.xml (Survey Log)
                addZipEntry(zos, "xl/worksheets/sheet4.xml", buildSurveyLogSheetXml(records))
            }
        }

        val formattedDate = dateFormat.format(Date())

        return ExportResult(
            file = targetFile,
            fileName = fileName,
            relativePath = "exports/excel/$fileName",
            recordsCount = records.size,
            generatedAt = formattedDate,
            isOoxmlZip = true
        )
    }

    private fun addZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun escapeXml(str: String?): String {
        if (str == null) return ""
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <fileVersion appName="xl" lastEdited="7" lowestEdited="7" rupBuild="24816"/>
  <workbookPr defaultThemeVersion="166925"/>
  <sheets>
    <sheet name="آبار المياه (Wells)" sheetId="1" r:id="rId1"/>
    <sheet name="العيون والينابيع (Springs)" sheetId="2" r:id="rId2"/>
    <sheet name="السدود والحواجز (Dams)" sheetId="3" r:id="rId3"/>
    <sheet name="سجل العمليات (Survey Log)" sheetId="4" r:id="rId4"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/></patternFill></fill>
  </fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="1" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun cellStr(col: String, row: Int, value: String, styleIndex: Int = 0): String {
        val sAttr = if (styleIndex > 0) " s=\"$styleIndex\"" else ""
        return "<c r=\"$col$row\" t=\"inlineStr\"$sAttr><is><t>${escapeXml(value)}</t></is></c>"
    }

    private fun cellNum(col: String, row: Int, value: Double, styleIndex: Int = 0): String {
        val sAttr = if (styleIndex > 0) " s=\"$styleIndex\"" else ""
        val formattedValue = if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.5f", value).trimEnd('0').trimEnd('.')
        }
        return "<c r=\"$col$row\"$sAttr><v>$formattedValue</v></c>"
    }

    private fun buildWellsSheetXml(records: List<SurveyRecord>): String {
        val headers = listOf(
            "كود السجل", "اسم البئر الميداني", "اسم الجامع / المساح", "المحافظة", "المديرية",
            "العزلة", "القرية", "خط العرض (Lat)", "خط الطول (Lon)", "الارتفاع (م)",
            "دقة الـ GPS (م)", "جودة الـ GPS", "عدد المرفقات", "ملفات المرفقات", "حالة السجل",
            "عدد التعديلات", "نوع البئر", "العمق (م)", "آلية الضخ", "الحالة التشغيلية", "تاريخ المسح"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1" customFormat="1" s="1">
""")
        headers.forEachIndexed { i, h ->
            val colLetter = getColLetter(i + 1)
            sb.append("      ").append(cellStr(colLetter, 1, h, 1)).append("\n")
        }
        sb.append("    </row>\n")

        records.forEachIndexed { idx, r ->
            val rowNum = idx + 2
            val w = r.wellDetails
            val gps = r.gpsPoint
            val attachCount = r.attachments.size
            val attachRefs = r.attachments.joinToString("; ") { it.fileName }
            val enumName = "${r.enumeratorUsername} (${r.enumeratorId})"

            val govDisplay = if (r.governorateNameSnapshotAr.isNotBlank()) "${r.governorateNameSnapshotAr} (${r.admin1Pcode})" else r.admin1Pcode
            val distDisplay = if (r.districtNameSnapshotAr.isNotBlank()) "${r.districtNameSnapshotAr} (${r.admin2Pcode})" else r.admin2Pcode
            val uzlahDisplay = if (r.subDistrictNameSnapshotAr.isNotBlank()) "${r.subDistrictNameSnapshotAr} (${r.admin3Pcode})" else r.admin3Pcode
            val vilDisplay = when {
                r.villageNameSnapshotAr != null -> "${r.villageNameSnapshotAr}${if (r.isLocalNameOverride) " [معدل محلياً]" else ""}"
                r.villageReferenceId != null -> r.villageReferenceId
                r.villageCode.isNotBlank() -> r.villageCode
                else -> "-"
            }

            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      ").append(cellStr("A", rowNum, r.recordId)).append("\n")
            sb.append("      ").append(cellStr("B", rowNum, w?.wellNameAr ?: "-")).append("\n")
            sb.append("      ").append(cellStr("C", rowNum, enumName)).append("\n")
            sb.append("      ").append(cellStr("D", rowNum, govDisplay)).append("\n")
            sb.append("      ").append(cellStr("E", rowNum, distDisplay)).append("\n")
            sb.append("      ").append(cellStr("F", rowNum, uzlahDisplay)).append("\n")
            sb.append("      ").append(cellStr("G", rowNum, vilDisplay)).append("\n")
            sb.append("      ").append(cellNum("H", rowNum, gps?.latitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("I", rowNum, gps?.longitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("J", rowNum, gps?.altitudeM ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("K", rowNum, (gps?.accuracyM ?: 0f).toDouble())).append("\n")
            sb.append("      ").append(cellStr("L", rowNum, gps?.quality?.titleAr ?: "غ/م")).append("\n")
            sb.append("      ").append(cellNum("M", rowNum, attachCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("N", rowNum, attachRefs.ifBlank { "لا يوجد" })).append("\n")
            sb.append("      ").append(cellStr("O", rowNum, r.workflowStatus)).append("\n")
            sb.append("      ").append(cellNum("P", rowNum, r.revisionCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("Q", rowNum, w?.wellType ?: "-")).append("\n")
            sb.append("      ").append(cellNum("R", rowNum, w?.wellDepthM ?: 0.0)).append("\n")
            sb.append("      ").append(cellStr("S", rowNum, w?.pumpingMechanism ?: "-")).append("\n")
            sb.append("      ").append(cellStr("T", rowNum, w?.operationalStatus ?: "-")).append("\n")
            sb.append("      ").append(cellStr("U", rowNum, r.createdAt)).append("\n")
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildSpringsSheetXml(records: List<SurveyRecord>): String {
        val headers = listOf(
            "كود السجل", "اسم العين / النبع", "اسم الجامع / المساح", "المحافظة", "المديرية",
            "العزلة", "القرية", "خط العرض (Lat)", "خط الطول (Lon)", "الارتفاع (م)",
            "دقة الـ GPS (م)", "جودة الـ GPS", "عدد المرفقات", "ملفات المرفقات", "حالة السجل",
            "عدد التعديلات", "معدل التدفق (لتر/ث)", "نقاء المياه", "الموسميّة والتدفق", "تاريخ المسح"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1" customFormat="1" s="1">
""")
        headers.forEachIndexed { i, h ->
            val colLetter = getColLetter(i + 1)
            sb.append("      ").append(cellStr(colLetter, 1, h, 1)).append("\n")
        }
        sb.append("    </row>\n")

        records.forEachIndexed { idx, r ->
            val rowNum = idx + 2
            val s = r.springDetails
            val gps = r.gpsPoint
            val attachCount = r.attachments.size
            val attachRefs = r.attachments.joinToString("; ") { it.fileName }
            val enumName = "${r.enumeratorUsername} (${r.enumeratorId})"

            val govDisplay = if (r.governorateNameSnapshotAr.isNotBlank()) "${r.governorateNameSnapshotAr} (${r.admin1Pcode})" else r.admin1Pcode
            val distDisplay = if (r.districtNameSnapshotAr.isNotBlank()) "${r.districtNameSnapshotAr} (${r.admin2Pcode})" else r.admin2Pcode
            val uzlahDisplay = if (r.subDistrictNameSnapshotAr.isNotBlank()) "${r.subDistrictNameSnapshotAr} (${r.admin3Pcode})" else r.admin3Pcode
            val vilDisplay = when {
                r.villageNameSnapshotAr != null -> "${r.villageNameSnapshotAr}${if (r.isLocalNameOverride) " [معدل محلياً]" else ""}"
                r.villageReferenceId != null -> r.villageReferenceId
                r.villageCode.isNotBlank() -> r.villageCode
                else -> "-"
            }

            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      ").append(cellStr("A", rowNum, r.recordId)).append("\n")
            sb.append("      ").append(cellStr("B", rowNum, s?.springNameAr ?: "-")).append("\n")
            sb.append("      ").append(cellStr("C", rowNum, enumName)).append("\n")
            sb.append("      ").append(cellStr("D", rowNum, govDisplay)).append("\n")
            sb.append("      ").append(cellStr("E", rowNum, distDisplay)).append("\n")
            sb.append("      ").append(cellStr("F", rowNum, uzlahDisplay)).append("\n")
            sb.append("      ").append(cellStr("G", rowNum, vilDisplay)).append("\n")
            sb.append("      ").append(cellNum("H", rowNum, gps?.latitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("I", rowNum, gps?.longitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("J", rowNum, gps?.altitudeM ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("K", rowNum, (gps?.accuracyM ?: 0f).toDouble())).append("\n")
            sb.append("      ").append(cellStr("L", rowNum, gps?.quality?.titleAr ?: "غ/م")).append("\n")
            sb.append("      ").append(cellNum("M", rowNum, attachCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("N", rowNum, attachRefs.ifBlank { "لا يوجد" })).append("\n")
            sb.append("      ").append(cellStr("O", rowNum, r.workflowStatus)).append("\n")
            sb.append("      ").append(cellNum("P", rowNum, r.revisionCount.toDouble())).append("\n")
            sb.append("      ").append(cellNum("Q", rowNum, s?.flowRateLps ?: 0.0)).append("\n")
            sb.append("      ").append(cellStr("R", rowNum, s?.waterClarity ?: "-")).append("\n")
            sb.append("      ").append(cellStr("S", rowNum, s?.dischargeSeasonality ?: "-")).append("\n")
            sb.append("      ").append(cellStr("T", rowNum, r.createdAt)).append("\n")
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildDamsSheetXml(records: List<SurveyRecord>): String {
        val headers = listOf(
            "كود السجل", "اسم السد / الحاجز", "اسم الجامع / المساح", "المحافظة", "المديرية",
            "العزلة", "القرية", "خط العرض (Lat)", "خط الطول (Lon)", "الارتفاع (م)",
            "دقة الـ GPS (م)", "جودة الـ GPS", "عدد المرفقات", "ملفات المرفقات", "حالة السجل",
            "عدد التعديلات", "نوع المنشأة المائية", "السعة التخزينية (م3)", "ارتفاع السد (م)", "الحالة الإنشائية", "تاريخ المسح"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1" customFormat="1" s="1">
""")
        headers.forEachIndexed { i, h ->
            val colLetter = getColLetter(i + 1)
            sb.append("      ").append(cellStr(colLetter, 1, h, 1)).append("\n")
        }
        sb.append("    </row>\n")

        records.forEachIndexed { idx, r ->
            val rowNum = idx + 2
            val d = r.damDetails
            val gps = r.gpsPoint
            val attachCount = r.attachments.size
            val attachRefs = r.attachments.joinToString("; ") { it.fileName }
            val enumName = "${r.enumeratorUsername} (${r.enumeratorId})"

            val govDisplay = if (r.governorateNameSnapshotAr.isNotBlank()) "${r.governorateNameSnapshotAr} (${r.admin1Pcode})" else r.admin1Pcode
            val distDisplay = if (r.districtNameSnapshotAr.isNotBlank()) "${r.districtNameSnapshotAr} (${r.admin2Pcode})" else r.admin2Pcode
            val uzlahDisplay = if (r.subDistrictNameSnapshotAr.isNotBlank()) "${r.subDistrictNameSnapshotAr} (${r.admin3Pcode})" else r.admin3Pcode
            val vilDisplay = when {
                r.villageNameSnapshotAr != null -> "${r.villageNameSnapshotAr}${if (r.isLocalNameOverride) " [معدل محلياً]" else ""}"
                r.villageReferenceId != null -> r.villageReferenceId
                r.villageCode.isNotBlank() -> r.villageCode
                else -> "-"
            }

            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      ").append(cellStr("A", rowNum, r.recordId)).append("\n")
            sb.append("      ").append(cellStr("B", rowNum, d?.damNameAr ?: "-")).append("\n")
            sb.append("      ").append(cellStr("C", rowNum, enumName)).append("\n")
            sb.append("      ").append(cellStr("D", rowNum, govDisplay)).append("\n")
            sb.append("      ").append(cellStr("E", rowNum, distDisplay)).append("\n")
            sb.append("      ").append(cellStr("F", rowNum, uzlahDisplay)).append("\n")
            sb.append("      ").append(cellStr("G", rowNum, vilDisplay)).append("\n")
            sb.append("      ").append(cellNum("H", rowNum, gps?.latitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("I", rowNum, gps?.longitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("J", rowNum, gps?.altitudeM ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("K", rowNum, (gps?.accuracyM ?: 0f).toDouble())).append("\n")
            sb.append("      ").append(cellStr("L", rowNum, gps?.quality?.titleAr ?: "غ/م")).append("\n")
            sb.append("      ").append(cellNum("M", rowNum, attachCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("N", rowNum, attachRefs.ifBlank { "لا يوجد" })).append("\n")
            sb.append("      ").append(cellStr("O", rowNum, r.workflowStatus)).append("\n")
            sb.append("      ").append(cellNum("P", rowNum, r.revisionCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("Q", rowNum, d?.structureType ?: "-")).append("\n")
            sb.append("      ").append(cellNum("R", rowNum, d?.storageCapacityM3 ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("S", rowNum, d?.damHeightM ?: 0.0)).append("\n")
            sb.append("      ").append(cellStr("T", rowNum, d?.structuralCondition ?: "-")).append("\n")
            sb.append("      ").append(cellStr("U", rowNum, r.createdAt)).append("\n")
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildSurveyLogSheetXml(records: List<SurveyRecord>): String {
        val headers = listOf(
            "#", "كود السجل", "نوع المسح", "اسم الموقع الميداني", "اسم الجامع / المساح",
            "المحافظة", "المديرية", "العزلة", "القرية", "خط العرض", "خط الطول", "الارتفاع (م)",
            "دقة الـ GPS (م)", "جودة الـ GPS", "عدد المرفقات والصور", "أسماء المرفقات",
            "حالة واعتماد السجل", "عدد مرات التعديل", "تاريخ ووقت الإنشاء", "تاريخ ووقت آخر تحديث"
        )

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1" customFormat="1" s="1">
""")
        headers.forEachIndexed { i, h ->
            val colLetter = getColLetter(i + 1)
            sb.append("      ").append(cellStr(colLetter, 1, h, 1)).append("\n")
        }
        sb.append("    </row>\n")

        records.forEachIndexed { idx, r ->
            val rowNum = idx + 2
            val siteName = r.wellDetails?.wellNameAr
                ?: r.springDetails?.springNameAr
                ?: r.damDetails?.damNameAr
                ?: "موقع غير مسمى"

            val gps = r.gpsPoint
            val attachCount = r.attachments.size
            val attachRefs = r.attachments.joinToString("; ") { it.fileName }
            val enumName = "${r.enumeratorUsername} (${r.enumeratorId})"

            val govDisplay = if (r.governorateNameSnapshotAr.isNotBlank()) "${r.governorateNameSnapshotAr} (${r.admin1Pcode})" else r.admin1Pcode
            val distDisplay = if (r.districtNameSnapshotAr.isNotBlank()) "${r.districtNameSnapshotAr} (${r.admin2Pcode})" else r.admin2Pcode
            val uzlahDisplay = if (r.subDistrictNameSnapshotAr.isNotBlank()) "${r.subDistrictNameSnapshotAr} (${r.admin3Pcode})" else r.admin3Pcode
            val vilDisplay = when {
                r.villageNameSnapshotAr != null -> "${r.villageNameSnapshotAr}${if (r.isLocalNameOverride) " [معدل محلياً]" else ""}"
                r.villageReferenceId != null -> r.villageReferenceId
                r.villageCode.isNotBlank() -> r.villageCode
                else -> "-"
            }

            sb.append("    <row r=\"$rowNum\">\n")
            sb.append("      ").append(cellNum("A", rowNum, (idx + 1).toDouble())).append("\n")
            sb.append("      ").append(cellStr("B", rowNum, r.recordId)).append("\n")
            sb.append("      ").append(cellStr("C", rowNum, r.surveyType.displayNameAr)).append("\n")
            sb.append("      ").append(cellStr("D", rowNum, siteName)).append("\n")
            sb.append("      ").append(cellStr("E", rowNum, enumName)).append("\n")
            sb.append("      ").append(cellStr("F", rowNum, govDisplay)).append("\n")
            sb.append("      ").append(cellStr("G", rowNum, distDisplay)).append("\n")
            sb.append("      ").append(cellStr("H", rowNum, uzlahDisplay)).append("\n")
            sb.append("      ").append(cellStr("I", rowNum, vilDisplay)).append("\n")
            sb.append("      ").append(cellNum("J", rowNum, gps?.latitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("K", rowNum, gps?.longitude ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("L", rowNum, gps?.altitudeM ?: 0.0)).append("\n")
            sb.append("      ").append(cellNum("M", rowNum, (gps?.accuracyM ?: 0f).toDouble())).append("\n")
            sb.append("      ").append(cellStr("N", rowNum, gps?.quality?.titleAr ?: "غ/م")).append("\n")
            sb.append("      ").append(cellNum("O", rowNum, attachCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("P", rowNum, attachRefs.ifBlank { "لا يوجد" })).append("\n")
            sb.append("      ").append(cellStr("Q", rowNum, r.workflowStatus)).append("\n")
            sb.append("      ").append(cellNum("R", rowNum, r.revisionCount.toDouble())).append("\n")
            sb.append("      ").append(cellStr("S", rowNum, r.createdAt)).append("\n")
            sb.append("      ").append(cellStr("T", rowNum, r.updatedAt)).append("\n")
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun getColLetter(colIndex: Int): String {
        var temp = colIndex
        var colName = ""
        while (temp > 0) {
            val rem = (temp - 1) % 26
            colName = (rem + 65).toChar() + colName
            temp = (temp - 1) / 26
        }
        return colName
    }
}
