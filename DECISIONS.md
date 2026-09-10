#  Architectural Decisions Record — Yemen Water Survey

**Version**: v0.8.0-auth-foundation  
**Commit Reference**: `23d2cf2`  
**Generated**: 2026-09-10  
**Total Decisions**: 16

---

## Purpose

This document captures architectural decisions made during development,
including the reasoning behind each choice, alternatives considered,
and conditions that would trigger revisiting the decision.

**Why this file exists:**
- Prevent "why did we do this?" questions months later
- Prevent re-litigating rejected alternatives
- Enable onboarding without tribal knowledge
- Protect against architectural regressions
- Support audits by donors and government stakeholders

## How to Use

- **Reading**: Use the Table of Contents to jump to specific decisions
- **Adding**: Append new decisions to `generate_decisions.py`, re-run, commit
- **Never edit**: Historical decisions are immutable — if a decision changes,
  add a NEW decision that references and supersedes the old one

---

## Table of Contents


### Phase 1

- [Decision 1.1: User Entity Schema (16 fields)](#decision-11-user-entity-schema-16-fields)
- [Decision 1.2: Indexing Strategy (6 indices)](#decision-12-indexing-strategy-6-indices)
- [Decision 1.3: Cryptographic PIN Storage & Verification Scheme](#decision-13-cryptographic-pin-storage--verification-scheme)
- [Decision 1.4: UserRole 4-Tier Hierarchy](#decision-14-userrole-4-tier-hierarchy)
- [Decision 1.5: Repository Contract with Flow + suspend + Dispatchers.IO](#decision-15-repository-contract-with-flow--suspend--dispatchersio)
- [Decision 1.6: AuthenticationResult Sealed Class](#decision-16-authenticationresult-sealed-class)
- [Decision 1.7: UserMapper with Credential Isolation](#decision-17-usermapper-with-credential-isolation)
- [Decision 1.8: In-Memory Session with 30-Minute Sliding Timeout](#decision-18-in-memory-session-with-30-minute-sliding-timeout)
- [Decision 1.9: Manual MIGRATION_7_8 with Raw SQL + IF NOT EXISTS](#decision-19-manual-migration_7_8-with-raw-sql--if-not-exists)

### Phase 2.5

- [Decision 2.1: Robolectric-Based Migration Testing](#decision-21-robolectric-based-migration-testing)
- [Decision 2.2: Test Dependencies (Robolectric 4.11.1 + Room-testing 2.6.1)](#decision-22-test-dependencies-robolectric-4111--room-testing-261)

### Phase 2.6

- [Decision 3.1: Schema Export to Git-Tracked JSON](#decision-31-schema-export-to-git-tracked-json)

### Cross-Cutting

- [Decision 4.1: Clean Architecture (Domain vs Data Separation)](#decision-41-clean-architecture-domain-vs-data-separation)
- [Decision 4.2: Naming Conventions (*Entity, *Dao, *Repository, *Impl)](#decision-42-naming-conventions-entity-dao-repository-impl)
- [Decision 4.3: Kotlin Coroutines + Flow (No LiveData/RxJava)](#decision-43-kotlin-coroutines--flow-no-livedatarxjava)
- [Decision 4.4: Device Hardware Binding (assignedDeviceId)](#decision-44-device-hardware-binding-assigneddeviceid)


---

# Phase 1

## Decision 1.1: User Entity Schema (16 fields)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/local/entity/UserEntity.kt`

### What

Single wide table with 16 fields covering identity (userId, username, fullNameAr/En), authentication (pinSalt, pinSaltedHash), authority scope (role, governorateCode, districtCode), device binding (assignedDeviceId, publicKeyBase64), lifecycle (isActive, provisionedBy, provisionedAt, lastLoginAt), and forward-compatible extension (metadataExtraJson).

### Why

- Support complete offline-first user lifecycle for Yemen Water Authority hierarchy
- All 16 fields are actively used (no speculative columns)
- Wide table performs better than joins for small user count (<1000)
- metadataExtraJson allows non-breaking schema evolution

### Alternatives Considered

- **Normalized schema (users + user_roles + user_scopes tables)**  
  _Rejected because_: Over-engineered for offline-first single-user-per-device reality
- **JSON blob for everything**  
  _Rejected because_: Loses queryability and index support

### Trade-offs Accepted

- Wide table (16 columns) requires careful mapping
- All fields loaded even when only some are needed

### Revisit Trigger

- If user count per device exceeds 5
- If role permissions become dynamic (not enum-driven)

---

## Decision 1.2: Indexing Strategy (6 indices)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/local/entity/UserEntity.kt`

### What

Six indices on users table: username (UNIQUE), role, governorateCode, districtCode, isActive, assignedDeviceId.

### Why

- username UNIQUE: enforces login uniqueness (queried every login)
- role: filter 'all supervisors' (queried in provisioning UI)
- governorateCode: scope filtering for governorate supervisors
- districtCode: scope filtering for district supervisors
- isActive: quickly enumerate active field workers
- assignedDeviceId: verify device binding on every login

### Alternatives Considered

- **Single composite index**  
  _Rejected because_: Query patterns are too varied to benefit from one composite
- **No indices (rely on table scan)**  
  _Rejected because_: Login latency would degrade as user table grows

### Trade-offs Accepted

- ~30% storage overhead per index
- Slower INSERTs (acceptable — users table is read-heavy)

### Revisit Trigger

- If user count exceeds 10,000 (unlikely for one authority)

---

## Decision 1.3: Cryptographic PIN Storage & Verification Scheme

**Status**: Accepted (revisited and confirmed 2024-12-19)  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/repository/UserRepositoryImpl.kt:298-323`

### What

Salted SHA-256 (single round) with 16-byte SecureRandom salt, Base64 encoding, and constant-time comparison. PIN length: 4 digits.

### Why

- Lost/stolen devices are typically factory-reset and sold, not forensically analyzed
- Industry precedent: iOS, Android, and banking ATMs use 4-digit PINs with equivalent security
- Salted SHA-256 prevents rainbow table attacks (main real-world threat)
- Constant-time comparison prevents timing side-channels
- Device binding (assignedDeviceId) adds second security layer
- Session timeout (30 min) limits exposure window

### Alternatives Considered

- **PBKDF2 (100k-600k iterations)**  
  _Rejected because_: Adds 200-500ms latency per login. Only meaningful vs sophisticated forensic attacks (unlikely in our threat model). 10,000-PIN keyspace is small enough that PBKDF2 offers only hours of protection.
- **Argon2**  
  _Rejected because_: No native Android SDK support; adds heavy dependency for marginal gain
- **BCrypt**  
  _Rejected because_: Same rationale as PBKDF2 rejection

### Trade-offs Accepted

-  Sophisticated attacker with physical device access AND DB extraction capability CAN brute-force PIN in <1 second on modern GPU
-  This threat model is out of scope for Yemen Water Authority field surveys
-  Simpler code = fewer bugs = better real-world security

### Compensating Controls

- Device binding (assignedDeviceId) — DB from Device A won't work on Device B
- Session timeout (30 min inactivity)
- Account deactivation capability (isActive flag)
- Future: SQLCipher database encryption (Phase 3+)

### Revisit Trigger

- If threat model changes (devices contain sensitive PII beyond survey data)
- If regulatory requirements mandate KDF usage
- If PIN length is extended to 6+ digits (then PBKDF2 becomes cost-effective)
- If SQLCipher adds performance budget for KDF

---

## Decision 1.4: UserRole 4-Tier Hierarchy

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/domain/model/UserRole.kt`

### What

Four-tier enum: ENUMERATOR, DISTRICT_SUPERVISOR, GOVERNORATE_SUPERVISOR, CENTRAL_SUPERVISOR. Stored as String.

### Why

- Directly mirrors Yemen Water Authority administrative structure
- Each tier has distinct authority scope (nothing → district → governorate → national)
- Enum enables exhaustive `when` at compile time
- String storage is human-readable in DB dumps

### Alternatives Considered

- **Permission-based RBAC**  
  _Rejected because_: Overkill for 4 well-defined roles
- **Boolean flags (isAdmin, canExport, etc.)**  
  _Rejected because_: Doesn't express hierarchy naturally
- **Integer storage (ordinal)**  
  _Rejected because_: Fragile on enum reorder; loses readability

### Trade-offs Accepted

- String storage is ~10 bytes vs 1 byte for int (negligible for <1000 users)
- Adding new role requires migration + code change

### Revisit Trigger

- If organizational structure adds new tier (e.g., regional layer)
- If permissions become user-specific (not role-derived)

---

## Decision 1.5: Repository Contract with Flow + suspend + Dispatchers.IO

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/domain/repository/UserRepository.kt`

### What

Repository interface uses Flow<T> for observable streams, suspend for one-shot operations, explicit Dispatchers.IO for all DB access, Result<T> for expected failures.

### Why

- Flow integrates natively with Jetpack Compose
- suspend enforces structured concurrency (no leaks)
- Explicit Dispatchers.IO prevents accidental main-thread DB access
- Result<T> makes expected errors part of the type signature

### Alternatives Considered

- **LiveData**  
  _Rejected because_: Legacy; not idiomatic for Compose
- **RxJava**  
  _Rejected because_: Heavy dependency; obsolete in modern Android
- **Callback-based API**  
  _Rejected because_: Coroutines are the Android standard since 2019

### Trade-offs Accepted

- Requires all contributors to understand coroutines
- Flow debugging can be harder than callbacks

### Revisit Trigger

- Never (coroutines are the Android standard)

---

## Decision 1.6: AuthenticationResult Sealed Class

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/domain/model/AuthenticationResult.kt`

### What

Sealed class with 6 outcomes: Success, InvalidCredentials, UserNotFound, UserInactive, DeviceMismatch, UnexpectedError. Each case carries typed data specific to that outcome.

### Why

- Login has 6 distinct outcomes requiring different UI handling
- Sealed class enables exhaustive `when` (compile-time safety)
- Typed data per case (e.g., attempt count for InvalidCredentials)
- Prevents null-checking anti-pattern

### Alternatives Considered

- **Boolean + separate error String**  
  _Rejected because_: Loses type safety; error strings become magic values
- **Exceptions for failures**  
  _Rejected because_: Expected failures shouldn't throw (performance + clarity)
- **Result<UserSession>**  
  _Rejected because_: Can't express 4 different failure types with typed data

### Trade-offs Accepted

- 6 classes to maintain (each is small data class)
- Adding new outcome requires touching all consumers (good — forces review)

### Revisit Trigger

- If failure types exceed ~10 (then reconsider design)

---

## Decision 1.7: UserMapper with Credential Isolation

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/mapper/UserMapper.kt`

### What

Mapper converts UserEntity ↔ User (domain). Domain User model OMITS pinSalt and pinSaltedHash fields. Username is normalized (lowercase, trim) before storage.

### Why

- Domain User MUST NOT expose credentials to ViewModels/UI
- Compile-time enforcement (fields don't exist to leak)
- Username normalization prevents duplicate accounts (Ali vs ali vs Ali )
- Clean Architecture: domain layer has zero Room imports

### Alternatives Considered

- **Expose Entity directly to UI**  
  _Rejected because_: Security leak risk; couples UI to persistence details
- **@Ignore on domain fields**  
  _Rejected because_: Room-specific annotation couples domain to Room framework

### Trade-offs Accepted

- Extra mapping code (small, testable)
- Two models to maintain (Entity + Domain)

### Revisit Trigger

- Never (Clean Architecture foundational principle)

---

## Decision 1.8: In-Memory Session with 30-Minute Sliding Timeout

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/session/SessionManager.kt`

### What

Session state held in-memory only (no disk persistence). 30-minute inactivity timeout with sliding renewal on activity. Process death forces re-authentication.

### Why

- In-memory: field devices shared between shifts — process kill forces re-auth
- 30 minutes: balances security (idle auto-logout) vs UX (doesn't interrupt surveys)
- Sliding: user activity extends session (doesn't punish long surveys)
- No token persistence = no token theft from disk

### Alternatives Considered

- **EncryptedSharedPreferences token**  
  _Rejected because_: Persists across process death (unwanted for shared devices)
- **No timeout (session until logout)**  
  _Rejected because_: Unattended devices = security risk
- **Fixed 8-hour session**  
  _Rejected because_: Doesn't account for idle time; wastes security budget

### Trade-offs Accepted

- Process restart = re-login (accepted; rare event)
- In-memory state lost on app crash (acceptable for security posture)

### Revisit Trigger

- If field workers complain about frequent re-authentication
- If devices become single-assignment (not shared between shifts)

---

## Decision 1.9: Manual MIGRATION_7_8 with Raw SQL + IF NOT EXISTS

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 1  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/local/migration/Migrations.kt`

### What

Manual Room Migration from schema v7 to v8. Uses raw SQL with 'CREATE TABLE IF NOT EXISTS' and 'CREATE INDEX IF NOT EXISTS' for idempotency. No foreign key constraints.

### Why

- Manual: full control over DDL, easy to review in PR
- IF NOT EXISTS: idempotent (safe to run twice, safe on partial failures)
- No FK constraints: users table is independent; FKs complicate future migrations
- Explicit SQL is auditable (unlike generated AutoMigration code)

### Alternatives Considered

- **Room AutoMigration**  
  _Rejected because_: Doesn't support all schema changes; generated code is opaque
- **Destructive migration (fallbackToDestructiveMigration)**  
  _Rejected because_: v7 databases contain real survey data — unacceptable data loss

### Trade-offs Accepted

- Manual SQL requires careful review (mitigated by MigrationTest suite)
- Every schema change requires new migration file

### Revisit Trigger

- When Room AutoMigration matures for complex scenarios (Room 3.x+)

---

# Phase 2.5

## Decision 2.1: Robolectric-Based Migration Testing

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 2.5  
**Evidence**: `android_app/app/src/test/java/com/yemen/watersurvey/data/local/migration/MigrationTest.kt`

### What

Migration tests run via Robolectric on JVM (not instrumented). Three tests validate: (1) schema correctness after migration, (2) data preservation from v7, (3) DAO interop on v8 database.

### Why

- Fast JVM execution (no emulator startup)
- Runs in CI without device farm dependency
- Real SQLite via Robolectric's shadow implementation
- 3 tests cover the critical migration failure modes

### Alternatives Considered

- **Instrumented tests (androidTest)**  
  _Rejected because_: Slow; requires device/emulator; harder in CI
- **No migration tests**  
  _Rejected because_: Too risky for production data

### Trade-offs Accepted

- Robolectric's SQLite ≠ Android device SQLite (rare edge cases)
- Accepted risk given speed and CI benefits

### Revisit Trigger

- If SQLite behavior differences cause production bugs

---

## Decision 2.2: Test Dependencies (Robolectric 4.11.1 + Room-testing 2.6.1)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 2.5  
**Evidence**: `android_app/app/build.gradle.kts`

### What

Added testImplementation dependencies: Robolectric 4.11.1, androidx.room:room-testing:2.6.1, kotlinx-coroutines-test.

### Why

- Versions matched to existing project (Room 2.6.1 in main deps)
- Latest stable releases with active maintenance
- Official Google/Robolectric support

### Alternatives Considered

- **Older Robolectric versions**  
  _Rejected because_: Known SQLite compatibility issues

### Trade-offs Accepted

- Adds ~15MB to test classpath (acceptable)

### Revisit Trigger

- Yearly, when new stable versions release

---

# Phase 2.6

## Decision 3.1: Schema Export to Git-Tracked JSON

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Phase 2.6  
**Evidence**: `android_app/app/schemas/com.yemen.watersurvey.data.local.WaterSurveyDatabase/8.json`

### What

Room configured to export schema JSON to app/schemas/. Files are Git-tracked. Directory structure: schemas/{database_class}/{version}.json

### Why

- schemas/8.json is source of truth for v8 database structure
- Git-tracked = code review catches accidental schema changes
- Enables automated migration validation
- Identity hash detects unintended entity modifications

### Alternatives Considered

- **.gitignore schemas/**  
  _Rejected because_: Loses migration safety net; no historical record
- **Manual schema documentation**  
  _Rejected because_: Drifts from reality over time

### Trade-offs Accepted

- Every schema change requires committing generated JSON
- Merge conflicts possible on parallel schema changes (rare)

### Revisit Trigger

- Never (Room best practice)

---

# Cross-Cutting

## Decision 4.1: Clean Architecture (Domain vs Data Separation)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Cross-Cutting  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/{domain,data}/`

### What

Two-layer separation: 'domain' package has zero Android/Room imports (pure Kotlin). 'data' package handles all persistence details (Room entities, DAOs, mappers).

### Why

- Domain layer testable in pure JVM (no Android runtime)
- Persistence details can change without touching business logic
- Matches existing project structure (FormPackage, SurveyRecord)
- Enforces separation of concerns at package level

### Alternatives Considered

- **Single package (no separation)**  
  _Rejected because_: Mixes concerns; harder to test; couples UI to Room
- **MVVM without domain layer**  
  _Rejected because_: Business logic leaks into ViewModels

### Trade-offs Accepted

- More files to maintain
- Requires mappers (already justified in Decision 1.7)

### Revisit Trigger

- Never (foundational architecture)

---

## Decision 4.2: Naming Conventions (*Entity, *Dao, *Repository, *Impl)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Cross-Cutting  
**Evidence**: `Repository-wide convention`

### What

Suffixes indicate role: UserEntity (Room), UserDao (Room), UserRepository (interface), UserRepositoryImpl (concrete). Domain models use plain names (User, UserRole).

### Why

- Matches existing project code (FormPackageEntity, SurveyRecordDao)
- Instantly recognizable role from name alone
- *Impl suffix distinguishes concrete from interface
- Domain models have no framework leak in naming

### Alternatives Considered

- **No suffixes (User in both layers)**  
  _Rejected because_: Name collision; import confusion
- **Prefixes (EntityUser, DaoUser)**  
  _Rejected because_: Non-idiomatic Kotlin

### Trade-offs Accepted

- Slightly longer names (acceptable for clarity)

### Revisit Trigger

- Never (project-wide convention)

---

## Decision 4.3: Kotlin Coroutines + Flow (No LiveData/RxJava)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Cross-Cutting  
**Evidence**: `All Repository and ViewModel classes`

### What

All async operations use Kotlin Coroutines. Observable streams use Flow. No LiveData or RxJava anywhere in codebase.

### Why

- Native Jetpack Compose integration
- Structured concurrency prevents leaks
- Flow for streams, suspend for one-shots (clean split)
- Google-recommended Android standard since 2020

### Alternatives Considered

- **LiveData**  
  _Rejected because_: Legacy; suboptimal for Compose
- **RxJava**  
  _Rejected because_: Heavy; obsolete for new Android projects

### Trade-offs Accepted

- Requires coroutines knowledge (already accepted)

### Revisit Trigger

- Never

---

## Decision 4.4: Device Hardware Binding (assignedDeviceId)

**Status**: Accepted  
**Date**: 2024-12-19  
**Phase**: Cross-Cutting  
**Evidence**: `android_app/app/src/main/java/com/yemen/watersurvey/data/repository/UserRepositoryImpl.kt`

### What

Each user record stores assignedDeviceId. Login validates current device ID matches stored value. First login on unbound account performs zero-touch bind. Admin can unbind for legitimate device replacement.

### Why

- Field devices assigned 1:1 to enumerators
- Prevents stolen credentials from working on attacker's device
- Zero-touch first bind: convenient for initial deployment
- Defense in depth: complements PIN security (Decision 1.3)

### Alternatives Considered

- **No device binding**  
  _Rejected because_: PIN alone is insufficient (credentials can be shared/stolen)
- **Hardware attestation (SafetyNet/Play Integrity)**  
  _Rejected because_: Requires Play Services (not available in all field regions)
- **Certificate-based device auth**  
  _Rejected because_: Overkill for Phase 1; adds provisioning complexity

### Trade-offs Accepted

- Device replacement requires admin action (rare event, acceptable)
- Zero-touch first bind = brief window of trust on initial login

### Revisit Trigger

- If certificate-based device identity becomes feasible in Phase 3+
- If Play Services becomes reliably available across deployment regions

---

## 📌 Adding New Decisions

To document a new architectural decision:

1. Open `generate_decisions.py`
2. Append a new dict to the `DECISIONS` list following this template:

```python
{
    "id": "X.Y",                    # e.g., "5.1"
    "title": "Short descriptive title",
    "phase": "Phase X",             # or "Cross-Cutting"
    "status": "Accepted",
    "date": "YYYY-MM-DD",
    "evidence": "path/to/file.kt:line",
    "what": "One-paragraph description of what was decided",
    "why": [
        "Reason 1",
        "Reason 2",
    ],
    "alternatives": [
        ("Alternative name", "Why it was rejected"),
    ],
    "tradeoffs": [
        "Accepted downside 1",
    ],
    "revisit": [
        "Condition that would trigger reconsideration",
    ],
},
```

3. Re-run: `python generate_decisions.py`
4. Review the updated `DECISIONS.md`
5. Commit both files together:
   ```
   git add generate_decisions.py DECISIONS.md
   git commit -m "docs: add Decision X.Y — [title]"
   ```

##  Immutability Rule

**Never edit historical decisions.** If a decision changes:
- Keep the old decision (mark status as "Superseded by Decision X.Y")
- Add a new decision explaining the change
- Reference the superseded decision by ID

##  Related Documentation

- `README.md` — Project overview
- `android_app/app/schemas/` — Database schema history
- Git commit history — Chronological change record

---

_Generated by `generate_decisions.py` on 2026-09-10_
_Do not edit this file directly — modify the script and regenerate_
