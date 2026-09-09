# CONTRACT GAP ANALYSIS REPORT

```
COMPLETION SELF-REPORT
======================
Step 1_10 (Read inputs):              [10/10]
Step 2_10 (Extract metadata reality): [10/10]
Step 3_10 (Extract XLSForm reality):  [10/10]
Step 4_10 (Compare vs target):        [10/10]
Step 5_10 (METADATA_CONTRACT.md):     [10/10]
Step 6_10 (XLSFORM_CONTRACT.md):      [10/10]
Step 7_10 (Gap analysis file):        [10/10]
Step 8_10 (Backlog file):             [10/10]
Step 9_10 (Self-check):               [10/10]
Step 10_10 (Summary):                 [10/10]

OVERALL: 100/100 = 100%

INTERPRETATION:
  ≥ 80%  → ACCEPTABLE, ready for human review

BLOCKERS ENCOUNTERED:
- None. Full codebase, parsers, entities, viewmodels, and export pipelines were inspected successfully.

QUESTIONS FOR HUMAN:
1. هل يُفضل اعتماد نسق الرمز الميداني الأبجدي الرقمي الحالي `ENUM-YEM-XXXXXX` أم فرض النسق الرقمي البحت `ENUM-\d{3,}`؟
2. هل يتم توحيد صيغة التوقيت الزمني لجميع الجداول عبر تطبيق ISO-8601 مع الإزاحة الزمنية (`+03:00`) بدلاً من التنسيق المحلي الحالي (`yyyy-MM-dd HH:mm:ss`)؟
3. هل هناك حاجة لدعم الأسئلة التكرارية (`begin_repeat` / `end_repeat`) في الإصدار الميداني الحالي أم تأجيلها للمرحلة القادمة؟
```

---

## 1. Executive Overview / نظرة عامة تنفيذية

يوثق هذا التقرير نتائج الفحص الشامل والمقارنة الدقيقة بين **العقود المستهدفة (Target Contracts)** لبيانات المسح والاستمارات، وبين **الواقع البرمجي الفعلي (Actual Code State)** المنفذ في تطبيق مسح المياه بالجمهورية اليمنية (Android Field Application).

تم تصنيف الفجوات وفق مستويات الخطورة القياسية:
- **Severity A (مستوى حرج):** مخاطر تداخل البيانات أو تلف السجلات (Data Corruption / Collision Risk).
- **Severity B (مستوى متوسط):** تحقق مفقود أو نقص في الميزات البرمجية (Missing Validation / Feature Gaps).
- **Severity C (مستوى طفيف):** اختلاف في التوثيق أو التسميات والمصطلحات دون تأثير وظيفي (Doc / Naming Mismatch).

---

## 2. Detailed Contract Gap Matrix / مصفوفة فجوات العقود

| # | Target Requirement (المتطلب المستهدف) | Current Code State (الواقع البرمجي الفعلي) | Gap Severity | File:Line Evidence (الدليل البرمجي) | Technical Explanation & Impact (الشرح والأثر التقني) |
| :-: | :--- | :--- | :---: | :--- | :--- |
| **GAP-01** | **Reserved Names Validation**<br>رفض الأسماء المحجوزة للأسئلة (`survey_uuid`, `enumerator_code`, `admin_*`, `gps_*`) لمنع تداخل الحقول. | أداة التحقق تفحص تكرار الأسماء والدورات المغلقة (Cycles) فقط، ولكنها **لا تحتوي على قائمة حظر للأسماء المحجوزة**. | **A** | [`FormPackageValidator.kt:117-187`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageValidator.kt#L117-L187)<br>[`SurveyViewModel.kt:782-855`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt#L782-L855) | إذا قام مصمم الاستمارة بتسمية سؤال باسم محجوز مثل `survey_uuid` أو `admin1Pcode`، سيحدث تضارب أثناء فك التسلسل (Deserialization) ودمج الإجابات مما قد يتسبب في كتابة خاطئة لحقول البيانات الوصفية. |
| **GAP-02** | **Submitted Timestamp & Timezone Offset**<br>تسجيل `created_at` و `submitted_at` بصيغة ISO-8601 متضمنة المنطقة الزمنية (`+03:00`). | السجلات تُخزن `createdAt` و `updatedAt` بصيغة محلية `yyyy-MM-dd HH:mm:ss` بدون إزاحة زمنية، ولا يوجد حقل صريح باسم `submittedAt` في جدول قاعدة البيانات. | **B** | [`SurveyRecordEntity.kt:51-52`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt#L51-L52)<br>[`SurveyViewModel.kt:105,557`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt#L105) | غياب الإزاحة الزمنية الرسمية قد يسبب التباساً عند مزامنة البيانات بين أجهزة متعددة بتوقيتات مختلفة. تتبع الاكتمال يتم حالياً عبر `workflowStatus = "COMPLETED"`. |
| **GAP-03** | **XLSForm Operators Support (`<`, `+`, `-`)**<br>دعم المعاملات الحسابية والمقارنات الكاملة في محرك التعبيرات. | المحلل يرفض صراحة المعاملات `<`, `+`, `/` ومعامل الطرح بين المتغيرات، ويقبل فقط `<=`, `*`, `div` والأرقام السالبة المباشرة. | **B** | [`Tokenizer.kt:135-185`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/Tokenizer.kt#L135-L185)<br>[`AstNode.kt:38-44`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/AstNode.kt#L38-L44) | تعبيرات XLSForm القياسية التي تستخدم `<` أو جمع المتغيرات `+` ستفشل أثناء المعالجة وتعتبر غير صالحة، مما يتطلب إعادة صياغتها باستخدام `>=` أو الدوال المدعومة. |
| **GAP-04** | **XLSForm `not()` Function Support**<br>دعم دالة النفي المنطقي `not()` ضمن التعبيرات الشرطية. | قائمة الدوال المعتمدة في المحرك تقتصر على: `if`, `concat`, `uuid`, `today`, `selected`, `count-selected`. دالة `not()` غير مسجلة. | **B** | [`AstNode.kt:42`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/AstNode.kt#L42)<br>[`ExpressionEvaluatorEngine.kt:107-156`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/expression/ExpressionEvaluatorEngine.kt#L107-L156) | تعبيرات التحقق مثل `not(selected(${uses}, 'irrigation'))` ترمي استثناء `ExpressionParserException` عند محاولة فكها لعدم التعرف على الدالة. |
| **GAP-05** | **Note & Repeat Types Support (`note`, `begin_repeat`)**<br>دعم نوع الملاحظات الإرشادية والأسئلة التكرارية. | المحلل يدعم النصوص والأرقام والخيارات والإحداثيات والصور والمجموعات، ولا يدعم `note` (تسقط كنص عادي) أو `repeat` (غير مدعومة). | **B** | [`FormPackageModels.kt:196-224`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/domain/model/FormPackageModels.kt#L196-L224)<br>[`FormDefinitionParser.kt:217-354`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormDefinitionParser.kt#L217-L354) | الاستمارات الحالية مخصصة لنقاط المياه الفردية (Well, Spring, Dam) ولا تحتوي على جداول فرعية، ولكن استيراد استمارات عامة تحتوي على `repeat` سيتجاهل العناصر المكررة. |
| **GAP-06** | **GPS Accuracy Gate Threshold (`≤ 20m` vs `< 15m`)**<br>بوابة دقة نظام تحديد المواقع العالمي. | التطبيق يفرض بوابة دقة **أكثر صرامة (< 15.0m)** لمنع الحفظ النهائي في حال تجاوزها، بينما يسمح الهدف بـ `≤ 20m`. | **C** | [`GpsCaptureState.kt:17`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/location/GpsCaptureState.kt#L17)<br>[`SurveyViewModel.kt:519-526`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt#L519-L526) | لا توجد مشكلة وظيفية؛ السلوك الفعلي في الكود يطبق معيار جودة جغرافي أعلى وأدق لحماية موثوقية السجلات الميدانية. |
| **GAP-07** | **Enumerator Code Generation Format**<br>نسق رمز الباحث الميداني المستهدف `ENUM-\d{3,}`. | التطبيق يُنشئ رمزاً بصيغة `ENUM-YEM-XXXXXX` (بادئة الدولة مع 6 خانات عشوائية أبجدية رقمية) لضمان عدم التكرار محلياً. | **C** | [`EnumeratorProfileManager.kt:23`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/identity/EnumeratorProfileManager.kt#L23)<br>[`SurveyRecordEntity.kt:29,940`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt#L29) | النسق الحالي يضمن تفادي التصادم عند العمل بدون اتصال مركزي، ويتطلب التوافق فقط تحديث قاعدة التحقق في حال الرغبة بحصرها على أرقام فقط. |
| **GAP-08** | **Form Packaging Contract (JSON vs Direct XLSX)**<br>قراءة استمارات XLSForm. | التطبيق يعتمد على حزم مضغوطة مهيكلة بصيغة JSON (`metadata.json`, `form_definition.json`) بدلاً من معالجة ملفات `.xlsx` على الهاتف مباشرة. | **C** | [`FormPackageManager.kt:35-43`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageManager.kt#L35-L43)<br>[`FormDefinitionParser.kt:9-18`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormDefinitionParser.kt#L9-L18) | خيار معماري مقصود لتحسين الأداء وتقليل استهلاك الذاكرة وتفادي تضمين مكتبات ثقيلة على نظام أندرويد. تم توثيقه بالكامل في عقد الاستمارات. |

---

## 3. Summary of Findings & Next Actions / ملخص النتائج والخطوات التالية

1. **الامتثال العام للعقود (Contract Compliance):**
   - تم تنفيذ بنية إدارة البيانات والبيانات الوصفية الأساسية (`survey_uuid`, `registry_code`, `admin_location`, `gps`, `wellDetailsJson`, `springDetailsJson`, `damDetailsJson`) بكفاءة وموثوقية عالية.
   - محرك التعبيرات الديناميكي ومحلل الحزم يعملان بنجاح مع كافة أنواع الأسئلة الميدانية الأساسية والمعتمدة في نماذج صعدة الإصدار السادس (`v6`).

2. **الفجوة الحرجة ذات الأولوية القصوى (Priority A):**
   - إضافة ميزة التحقق من قائمة الأسماء المحجوزة في `FormPackageValidator.kt` لمنع أي تداخل محتمل بين أسئلة النماذج وحقول قاعدة البيانات والتبادل.

3. **التحسينات المستقبلية (Priority B & C):**
   - ترقية معالجة التوقيت الزمني لدعم ISO-8601 مع إزاحة التوقيت (`+03:00`).
   - توسيع مكتبة المحلل اللغوي لدعم دالة `not()` والمعاملات الحسابية الإضافية.
