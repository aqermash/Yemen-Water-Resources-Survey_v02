package com.yemen.watersurvey.core.form

import com.yemen.watersurvey.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Structural Validator & Cycle Detector for Survey Form Packages.
 *
 * Implements Phase 12A validation requirements under C6.1.2:
 * - Structural validation of required files (metadata.json, form_definition.json, choices.json)
 * - Schema version and metadata completeness checks
 * - Questionnaire element uniqueness validation
 * - Choice list referential integrity validation
 * - Administrative tier binding validation
 * - Expression dependency extraction and Directed Acyclic Graph (DAG) cycle detection
 * - Rejects circular dependencies with INVALID_PACKAGE_STRUCTURE
 */
class FormPackageValidator(
    private val parser: FormDefinitionParser = FormDefinitionParser()
) {

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = "1.0"
    }

    /**
     * Performs comprehensive offline structural and logical validation of a form package directory.
     */
    fun validatePackage(packageDir: File): PackageValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val checkedFiles = mutableListOf<String>()

        if (!packageDir.exists() || !packageDir.isDirectory) {
            return PackageValidationResult(
                isValid = false,
                errors = listOf("المجلد المحدد للحزمة غير موجود أو غير صالح: ${packageDir.absolutePath}")
            )
        }

        // 1. Required: metadata.json
        val metadataFile = File(packageDir, FormPackageManager.REQUIRED_METADATA_FILE)
        var parsedMetadata: PackageMetadata? = null

        if (!metadataFile.exists()) {
            errors.add("الملف الإلزامي مفقود: ${FormPackageManager.REQUIRED_METADATA_FILE} (بيانات وصف الحزمة)")
        } else {
            checkedFiles.add(FormPackageManager.REQUIRED_METADATA_FILE)
            try {
                val metaContent = metadataFile.readText().trim()
                parsedMetadata = parser.parseMetadata(metaContent)

                if (parsedMetadata.formId.isBlank()) {
                    errors.add("حقل 'formId' فارغ أو غير موجود في ${FormPackageManager.REQUIRED_METADATA_FILE}")
                }
                if (parsedMetadata.version.isBlank()) {
                    errors.add("حقل 'version' فارغ أو غير موجود في ${FormPackageManager.REQUIRED_METADATA_FILE}")
                }
                if (parsedMetadata.name.isBlank()) {
                    errors.add("حقل 'name' فارغ أو غير موجود في ${FormPackageManager.REQUIRED_METADATA_FILE}")
                }
                if (parsedMetadata.schemaVersion.isNotBlank() && parsedMetadata.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                    warnings.add("إصدار Schema (${parsedMetadata.schemaVersion}) غير متطابق مع الإصدار القياسي المدعوم ($SUPPORTED_SCHEMA_VERSION)")
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في ${FormPackageManager.REQUIRED_METADATA_FILE}: ${e.message}")
            }
        }

        // 2. Required: choices.json
        val choicesFile = File(packageDir, FormPackageManager.REQUIRED_CHOICES_FILE)
        var parsedChoiceLists: Map<String, ChoiceList> = emptyMap()

        if (!choicesFile.exists()) {
            errors.add("الملف الإلزامي مفقود: ${FormPackageManager.REQUIRED_CHOICES_FILE} (خيارات القوائم المنسدلة والتصنيفات)")
        } else {
            checkedFiles.add(FormPackageManager.REQUIRED_CHOICES_FILE)
            try {
                val choicesContent = choicesFile.readText().trim()
                parsedChoiceLists = parser.parseChoiceLists(choicesContent)
                if (parsedChoiceLists.isEmpty()) {
                    warnings.add("ملف ${FormPackageManager.REQUIRED_CHOICES_FILE} لا يحتوي على أي قوائم خيارات")
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في ${FormPackageManager.REQUIRED_CHOICES_FILE}: ${e.message}")
            }
        }

        // 3. Required: form_definition.json
        val formDefFile = File(packageDir, FormPackageManager.REQUIRED_FORM_DEF_FILE)
        var parsedFormDef: FormDefinition? = null
        val dependencyGraph = mutableMapOf<String, Set<String>>()

        if (!formDefFile.exists()) {
            errors.add("الملف الإلزامي مفقود: ${FormPackageManager.REQUIRED_FORM_DEF_FILE} (تعريف أسئلة وحقول الاستمارة)")
        } else {
            checkedFiles.add(FormPackageManager.REQUIRED_FORM_DEF_FILE)
            try {
                val formDefContent = formDefFile.readText().trim()
                parsedFormDef = parser.parseFormDefinition(formDefContent)

                if (parsedFormDef.formId.isBlank() && parsedMetadata != null) {
                    parsedFormDef = parsedFormDef.copy(formId = parsedMetadata.formId)
                }
                if (parsedFormDef.version.isBlank() && parsedMetadata != null) {
                    parsedFormDef = parsedFormDef.copy(version = parsedMetadata.version)
                }

                if (parsedFormDef.rootElements.isEmpty()) {
                    errors.add("ملف ${FormPackageManager.REQUIRED_FORM_DEF_FILE} لا يحتوي على عناصر أو أسئلة في الاستمارة")
                } else {
                    // Validate element uniqueness & choice references
                    val allElements = parsedFormDef.allElements
                    val seenNames = mutableSetOf<String>()

                    for (elem in allElements) {
                        if (elem.name.isBlank()) {
                            errors.add("يوجد عنصر استمارة بدون اسم معرف ('name')")
                            continue
                        }
                        if (elem.name in seenNames) {
                            errors.add("تكرار في أسماء عناصر الاستمارة: تم العثور على اسم مكرر '${elem.name}'")
                        } else {
                            seenNames.add(elem.name)
                        }

                        // Validate question elements
                        if (elem is QuestionElement) {
                            if (elem.dataType == QuestionDataType.SELECT_ONE || elem.dataType == QuestionDataType.SELECT_MULTIPLE) {
                                val listName = elem.choicesListName
                                if (listName.isNullOrBlank()) {
                                    errors.add("السؤال '${elem.name}' من نوع ${elem.dataType} لا يحتوي على اسم قائمة الخيارات ('choicesListName')")
                                } else if (!parsedChoiceLists.containsKey(listName)) {
                                    errors.add("قائمة الخيارات '$listName' المشار إليها في السؤال '${elem.name}' غير موجودة في ${FormPackageManager.REQUIRED_CHOICES_FILE}")
                                }
                            } else if (elem.dataType == QuestionDataType.ADMIN_SELECT) {
                                if (elem.adminBinding == null) {
                                    warnings.add("السؤال الإداري '${elem.name}' لا يحتوي على ربط إداري صريح ('adminBinding')")
                                }
                            }
                        }
                    }

                    // Build Dependency Graph
                    for (elem in allElements) {
                        val deps = mutableSetOf<String>()
                        elem.relevantExpr?.let { expr ->
                            deps.addAll(expr.referencedFields)
                        }
                        if (elem is CalculateElement) {
                            deps.addAll(elem.calculationExpr.referencedFields)
                        }
                        if (elem is QuestionElement) {
                            elem.constraintExpr?.let { expr ->
                                // Exclude self-reference on constraints ('.' or self field name)
                                val externalRefs = expr.referencedFields.filter { it != elem.name }
                                deps.addAll(externalRefs)
                            }
                            elem.choiceFilterExpr?.let { expr ->
                                deps.addAll(expr.referencedFields)
                            }
                        }

                        if (deps.isNotEmpty()) {
                            dependencyGraph[elem.name] = deps
                        }
                    }

                    // Check for undefined referenced variables
                    val elementNames = parsedFormDef.elementIndex.keys
                    for ((elemName, deps) in dependencyGraph) {
                        for (dep in deps) {
                            if (dep !in elementNames) {
                                warnings.add("الحقل '${dep}' المشار إليه في تعبيرات '${elemName}' غير معرف في عناصر الاستمارة")
                            }
                        }
                    }

                    // Cycle Detection via DFS
                    val cycleErrors = detectCycles(dependencyGraph)
                    if (cycleErrors.isNotEmpty()) {
                        errors.addAll(cycleErrors)
                    }
                }
            } catch (e: Exception) {
                errors.add("تنسيق JSON غير صالح في ${FormPackageManager.REQUIRED_FORM_DEF_FILE}: ${e.message}")
            }
        }

        // 4. Optional: official_template.pdf
        val templatePdf = File(packageDir, FormPackageManager.OPTIONAL_TEMPLATE_PDF)
        if (templatePdf.exists()) {
            checkedFiles.add(FormPackageManager.OPTIONAL_TEMPLATE_PDF)
            if (templatePdf.length() == 0L) {
                warnings.add("ملف ${FormPackageManager.OPTIONAL_TEMPLATE_PDF} فارغ (0 بايت)")
            }
        }

        // 5. Optional: pdf_mapping.json
        val pdfMappingFile = File(packageDir, FormPackageManager.OPTIONAL_PDF_MAPPING)
        if (pdfMappingFile.exists()) {
            checkedFiles.add(FormPackageManager.OPTIONAL_PDF_MAPPING)
            try {
                val json = JSONObject(pdfMappingFile.readText())
                if (!json.has("fields")) {
                    warnings.add("ملف ${FormPackageManager.OPTIONAL_PDF_MAPPING} لا يحتوي على مصفوفة حقول الإحداثيات 'fields'")
                }
            } catch (e: Exception) {
                warnings.add("تنبيه: تنسيق JSON غير قياسي في ${FormPackageManager.OPTIONAL_PDF_MAPPING}: ${e.message}")
            }
        }

        // 6. Optional: sequence_pool.json
        val sequencePoolFile = File(packageDir, FormPackageManager.OPTIONAL_SEQUENCE_POOL)
        if (sequencePoolFile.exists()) {
            checkedFiles.add(FormPackageManager.OPTIONAL_SEQUENCE_POOL)
            try {
                val ranges = parser.parseSequencePool(sequencePoolFile.readText())
                if (ranges.isEmpty()) {
                    warnings.add("ملف ${FormPackageManager.OPTIONAL_SEQUENCE_POOL} لا يحتوي على نطاقات ترقيم صالحة")
                }
            } catch (e: Exception) {
                warnings.add("تنبيه: تنسيق JSON غير صالح في ${FormPackageManager.OPTIONAL_SEQUENCE_POOL}: ${e.message}")
            }
        }

        val checksum = if (errors.isEmpty()) calculateDirectoryChecksum(packageDir) else ""

        return PackageValidationResult(
            isValid = errors.isEmpty() && parsedMetadata != null && parsedFormDef != null,
            metadata = parsedMetadata,
            formDefinition = parsedFormDef,
            choiceLists = parsedChoiceLists,
            errors = errors,
            warnings = warnings,
            checkedFiles = checkedFiles,
            calculatedChecksum = checksum,
            dependencyGraph = dependencyGraph
        )
    }

    /**
     * Detects cycles in the variable dependency graph using 3-color Depth First Search.
     * Returns a list of error messages for any cycles found with full traversal cycle paths.
     */
    fun detectCycles(graph: Map<String, Set<String>>): List<String> {
        val errors = mutableListOf<String>()
        val state = mutableMapOf<String, Int>() // 0 = UNVISITED, 1 = VISITING, 2 = VISITED
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            state[node] = 1 // VISITING
            path.add(node)

            val neighbors = graph[node] ?: emptySet()
            for (next in neighbors) {
                val nextState = state[next] ?: 0
                if (nextState == 1) {
                    // Cycle detected!
                    val cycleStartIndex = path.indexOf(next)
                    val cycleNodes = if (cycleStartIndex >= 0) {
                        path.subList(cycleStartIndex, path.size) + next
                    } else {
                        listOf(node, next, node)
                    }
                    val pathStr = cycleNodes.joinToString(" -> ")
                    errors.add("INVALID_PACKAGE_STRUCTURE: تم اكتشاف تبعية دائرية مغلقة بين الحقول (Circular variable dependency detected): [$pathStr]")
                } else if (nextState == 0) {
                    dfs(next)
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2 // VISITED
        }

        for (node in graph.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node)
            }
        }

        return errors
    }

    /**
     * Calculates SHA-256 checksum across all files in a package directory deterministically.
     */
    fun calculateDirectoryChecksum(directory: File): String {
        if (!directory.exists() || !directory.isDirectory) return ""
        val digest = MessageDigest.getInstance("SHA-256")

        val files = directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(directory).path }
            .toList()

        for (file in files) {
            val relativePath = file.relativeTo(directory).path.toByteArray(Charsets.UTF_8)
            digest.update(relativePath)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
