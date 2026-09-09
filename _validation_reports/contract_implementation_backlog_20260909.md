# CONTRACT IMPLEMENTATION BACKLOG

**Generated Date:** 2026-09-09  
**Target Milestone:** Contract Convergence & Robustness Hardening  
**Scope:** Closing gaps between Target Specifications and Actual Codebase Implementation  
**Language:** Hybrid (English Headers & Structural Tags, Arabic Descriptions & Acceptance Criteria)

---

## 1. Overview & Prioritization Strategy / نظرة عامة واستراتيجية الأولويات

يحدد هذا المستند قائمة المهام البرمجية المرتبة والمفصلة لسد الفجوات المكتشفة في تقرير تحليل العقود (`contract_gap_analysis_20260909.md`).
تم ترتيب المهام وفق منهجية صارمة تعطي الأولوية لـ:
1. **المهام ذات الأولوية A (حماية سلامة البيانات ومنع التضارب):** يجب تنفيذها أولاً لتفادي أي خطر على البيانات.
2. **المهام ذات الأولوية B (استكمال قواعد التحقق والميزات المفقودة):** تعزيز التوافق مع معايير XLSForm والتوقيت الزمني.
3. **المهام ذات الأولوية C (تحسينات التوثيق وتوحيد التنسيقات):** مواءمة الأنماط غير المؤثرة وظيفياً.

---

## 2. Prioritized Task Backlog / قائمة المهام المرتبة

### 🔴 Phase A: Critical Integrity & Conflict Prevention (الأولوية القصوى)

#### [TASK-A01] Reserved Keyword Validator in Form Package Ingestion
- **Priority:** `CRITICAL (Priority A)`
- **Effort:** `S (Small: 1-2 hours)`
- **Category:** Integrity / Validation Gate
- **Files Affected:**
  - [`core/form/FormPackageValidator.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageValidator.kt)
  - `test/java/com/yemen/watersurvey/core/form/FormPackageValidatorTest.kt`
- **Technical Description:**
  إضافة فحص إلزامي لقائمة الكلمات والأسماء المحجوزة في أداة التحقق من حزم النماذج (`FormPackageValidator`). في حال احتواء أي سؤال أو حقل في `form_definition.json` على اسم يطابق حقلاً محجوزاً (مثل: `survey_uuid`, `record_id`, `registry_code`, `enumerator_code`, `admin1_pcode`, `admin2_pcode`, `admin3_pcode`, `latitude`, `longitude`, `altitude_m`, `accuracy_m`, `gps_*`)، يتم رفض الحزمة فوراً وإرجاع خطأ هيكلي واضح `INVALID_ELEMENT_RESERVED_NAME`.
- **Acceptance Criteria (معايير القبول):**
  1. تعريف قائمة ثابتة للكلمات المحجوزة `RESERVED_FIELD_NAMES` مطابقة لتوثيق عقد البيانات الوصفية.
  2. رفض أي استمارة تحتوي على حقل باسم محجوز وإصدار رسالة خطأ باللغتين العربية والإنجليزية.
  3. كتابة اختبارات وحدة (Unit Tests) تؤكد رفض الحزم المخالفة وقبول الحزم القياسية.

---

### 🟡 Phase B: Validation, Expressions & Standards Expansion (الأولوية المتوسطة)

#### [TASK-B01] ISO-8601 Timestamp Formatting with Timezone Offset
- **Priority:** `MEDIUM (Priority B)`
- **Effort:** `S (Small: 2 hours)`
- **Category:** Metadata Standardization
- **Files Affected:**
  - [`data/entity/SurveyRecordEntity.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt)
  - [`presentation/viewmodel/SurveyViewModel.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt)
  - [`core/sync/SurveySyncExporter.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/sync/SurveySyncExporter.kt)
  - [`core/location/GpsCaptureManager.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/location/GpsCaptureManager.kt)
- **Technical Description:**
  ترقية نسق التوقيت عبر كامل النظام من `yyyy-MM-dd HH:mm:ss` إلى التنسيق العالمي القياسي ISO-8601 متضمناً الإزاحة الزمنية لليمن (`yyyy-MM-dd'T'HH:mm:ssXXX` مثل `2026-09-09T15:30:00+03:00`). إضافة حقل اختياري `submittedAt` في كينونة السجل يُسجل لحظة اكتمال الاستمارة (`workflowStatus = "COMPLETED"`).
- **Acceptance Criteria (معايير القبول):**
  1. جميع التواريخ الصادرة في حزم المزامنة (`.ywsync`) وتصدير السجلات تحتوي على الإزاحة الزمنية القياسية.
  2. تحديث اختبارات المزامنة والتحقق لتتوافق مع الصيغة الجديدة دون كسر التوافق العكسي مع السجلات المخزنة سابقاً.

---

#### [TASK-B02] Expression Engine Logical Function `not()` & Unary Operator
- **Priority:** `MEDIUM (Priority B)`
- **Effort:** `S (Small: 2-3 hours)`
- **Category:** Expression Engine Enhancement
- **Files Affected:**
  - [`core/expression/AstNode.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/AstNode.kt)
  - [`core/expression/Tokenizer.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/Tokenizer.kt)
  - [`core/expression/ExpressionEvaluatorEngine.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/ExpressionEvaluatorEngine.kt)
  - `test/java/com/yemen/watersurvey/core/expression/FormExpressionEvaluatorTest.kt`
- **Technical Description:**
  إضافة دالة `not()` إلى قائمة الدوال المعتمدة في محلل التعبيرات الرياضية والمنطقية. تقييم الدالة عبر عكس القيمة المنطقية للتعبير الداخلي `!toBoolean(arg)`.
- **Acceptance Criteria (معايير القبول):**
  1. نجاح معالجة تعبيرات مثل `not(selected(${water_source}, 'none'))` و `not(${depth} > 100)`.
  2. إضافة حالات اختبار في ملف الاختبار الشامل للتعبيرات للتأكد من سلوك النفي على القيم الفارغة والأرقام والنصوص.

---

#### [TASK-B03] XLSForm Arithmetic Operators Expansion (`<`, `+`, infix `-`)
- **Priority:** `MEDIUM (Priority B)`
- **Effort:** `M (Medium: 4-6 hours)`
- **Category:** Expression Engine Grammar
- **Files Affected:**
  - [`core/expression/Tokenizer.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/Tokenizer.kt)
  - [`core/expression/AstNode.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/AstNode.kt)
  - [`core/expression/ExpressionEvaluatorEngine.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/ExpressionEvaluatorEngine.kt)
- **Technical Description:**
  توسيع المعجم اللغوي (Tokenizer) ومحلل الشجرة (AST Parser) لدعم معامل الأصغر البحت `<`, ومعامل الجمع الرياضي `+`، ومعامل الطرح بين المتغيرات `a - b` مع الحفاظ على التمييز بينه وبين الأرقام السالبة والرموز النصية.
- **Acceptance Criteria (معايير القبول):**
  1. التحقق من أسبقية المعاملات الحسابية (الضرب والقسمة أولاً، ثم الجمع والطرح).
  2. معالجة تعبيرات المقارنة المركبة بنجاح ودون أي خطأ في التحليل اللغوي.

---

#### [TASK-B04] Read-Only Note Question Type Implementation
- **Priority:** `LOW-MEDIUM (Priority B)`
- **Effort:** `M (Medium: 4 hours)`
- **Category:** Dynamic UI / Questionnaire Engine
- **Files Affected:**
  - [`domain/model/FormPackageModels.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/domain/model/FormPackageModels.kt)
  - [`core/form/FormDefinitionParser.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormDefinitionParser.kt)
  - `presentation/form/QuestionInputComposables.kt`
- **Technical Description:**
  دعم نوع السؤال الإرشادي `note` في `QuestionDataType`. بناء عنصر واجهة مستخدم مخصص في Jetpack Compose يعرض النصوص التوجيهية أو التحذيرات الميدانية دون تقديم حقل إدخال للمستخدم ودون تخزين قيمة في البيانات المحفوظة.
- **Acceptance Criteria (معايير القبول):**
  1. التعرف على نوع `note` أثناء استيراد النماذج وتوليد عنصر `NoteElement`.
  2. عرض بطاقة إرشادية بتصميم Material 3 متناسق مع دعم التعبير الشرطي للظهور (`relevant`).

---

### 🟢 Phase C: Documentation, Formatting & Minor Polish (الأولوية الطفيفة)

#### [TASK-C01] Enumerator Code Schema Configuration Option
- **Priority:** `LOW (Priority C)`
- **Effort:** `S (Small: 1 hour)`
- **Category:** Profile Management
- **Files Affected:**
  - [`core/identity/EnumeratorProfileManager.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/identity/EnumeratorProfileManager.kt)
- **Technical Description:**
  إتاحة خيار تكوين مرن لتوليد رمز الباحث الميداني، إما بالنمط الأبجدي الرقمي الحالي `ENUM-YEM-XXXXXX` أو النمط الرقمي البحت `ENUM-XXX`، مع الحفاظ على مبدأ عدم إدخال الأسماء يدوياً وتثبيت الرمز في قاعدة البيانات والتتبع.
- **Acceptance Criteria (معايير القبول):**
  1. الحفاظ على الرمز المولد وعدم تغييره عبر جلسات التطبيق المتكررة.
  2. توافق كامل مع جداول المراجعة والمزامنة.

---

## 3. Summary Schedule & Execution Milestones / جدول التنفيذ المقترح

| Milestone | Tasks Included | Target Timeframe | Primary Deliverables |
| :--- | :--- | :---: | :--- |
| **Milestone 1 (Security & Integrity)** | TASK-A01 | Sprint 1 (Day 1) | Reserved Keyword Filter & Comprehensive Tests |
| **Milestone 2 (Standardization)** | TASK-B01, TASK-B02 | Sprint 1 (Day 2) | ISO-8601 Timestamps & Logical `not()` function |
| **Milestone 3 (Grammar & UI)** | TASK-B03, TASK-B04 | Sprint 2 (Day 3-4) | Full Arithmetic Precedence & Read-Only Notes |
| **Milestone 4 (Final Polish)** | TASK-C01 | Sprint 2 (Day 5) | Enumerator Code Configuration & Contract Re-Audit |
