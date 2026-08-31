# Phase 13: Lightweight Kotlin Expression Engine

## Objective
Implement a high-performance, lightweight expression engine in pure Kotlin. This engine will parse and evaluate algebraic calculations, comparison checks, and complex boolean logic in form constraints and relevancy rules, referencing current form values without external JVM scripts.

## Current Project Context
Currently, conditional field visibility and calculations are hardcoded in Kotlin code inside the ViewModels. To support fully dynamic forms, validation rules and display logic must be written as declarative expressions inside the form definition JSON files. A safe, deterministic evaluator is required to run these expressions in-memory on the device.

## Existing Files To Inspect
- **Dynamic Renderer:** [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyFormsScreen.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyFormsScreen.kt)
- **ViewModel State:** [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt)
- **Audit Findings:** [`docs/AUDITS/MWATER_REFERENCE_AUDIT.md`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/docs/AUDITS/MWATER_REFERENCE_AUDIT.md) (inspect section 6, Expression Engine Analysis)

## Requirements
1. **Lightweight Kotlin Expression Evaluator:**
   - Develop a pure-Kotlin lexer and parser mapping infix expressions to an Abstract Syntax Tree (AST) or Reverse Polish Notation (RPN).
   - Evaluator must be highly performant, type-safe, and dependency-free.
2. **AST-Based Approach:**
   - Nodes represent operations (`Add`, `Subtract`, `And`, `Or`, `Equal`, etc.) or values (`Literal`, `FieldReference`).
   - The evaluation is run recursively against a value context map.
3. **Field References:**
   - Parse variables denoted by `${field_name}` and retrieve their current values from the active form state map.
4. **Calculations & Conditions:**
   - Support arithmetic: `+`, `-`, `*`, `/`, `%`
   - Support comparisons: `=`, `!=`, `<`, `<=`, `>`, `>=`
   - Support boolean logic: `and`, `or`, `not`
   - Support standard functions: `selected()`, `coalesce()`, `if()`, `string-length()`

## Implementation Steps
1. **Tokenizer (Lexer):** Convert raw string expressions (e.g. `"${depth} > 50 and ${status} = 'active'"`) into a list of logical tokens.
2. **Parser (Parser):** Implement the Shunting-yard algorithm or recursive descent parser to convert tokens into a structured syntax tree.
3. **Evaluator (Evaluator):** Develop the evaluation visitor pattern resolving nodes into boolean, numeric, or string results using current form data.
4. **Integration Layer:** Wire the engine with the dynamic form state so changes in form values automatically trigger recalculation of dependent fields.
5. **Testing Suite:** Build comprehensive unit tests verifying standard mathematical rules, string matching, and boundary conditions.

## Constraints / Do Not Change
- **Do NOT use Kotlin script evaluation (`javax.script`), JavaScript engines (Rhino/V8), or complex runtime compiler tools.** They are slow, increase binary size, and present security/memory issues on Android.
- **Do NOT throw exceptions during typing evaluations.** Safe defaults (e.g. return null or false) must be returned on parsing/evaluation errors to keep the application stable.

## Testing Requirements
- **Precision Verification:** Unit test operator precedence (e.g. `2 + 3 * 4 == 14`), boolean parenthesization, and decimal arithmetic.
- **Null Safety Tests:** Verify behavior when field references evaluate to empty strings, null values, or missing properties.
- **Performance Benchmarks:** Run evaluation loops to ensure updates run under 1 ms per form update cycle.

## Final Report Requirements
Compile a walkthrough showing:
- Verification of test scenarios (calculating fields, toggle relevancies).
- Evaluation duration reports showing processing times under maximum layout depths.
