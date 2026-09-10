"""
generate_decisions.py
---------------------
Generates DECISIONS.md for Yemen Water Survey project.

Usage:
    python generate_decisions.py

Output:
    DECISIONS.md at the same directory as this script.

To add a new decision:
    1. Append a new dict to the DECISIONS list below
    2. Re-run the script
    3. Review and commit the updated DECISIONS.md
"""

from datetime import datetime
from pathlib import Path

# ==============================================================================
# PROJECT METADATA
# ==============================================================================
PROJECT_NAME = "Yemen Water Survey"
PROJECT_VERSION = "v0.8.0-auth-foundation"
COMMIT_REF = "23d2cf2"
GENERATED_DATE = datetime.now().strftime("%Y-%m-%d")

# ==============================================================================
# DECISIONS DATA
# ==============================================================================
DECISIONS = [
    {
        "id": "1.1",
        "title": "User Entity Schema (16 fields)",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/local/entity/UserEntity.kt",
        "what": (
            "Single wide table with 16 fields covering identity "
            "(userId, username, fullNameAr/En), authentication (pinSalt, "
            "pinSaltedHash), authority scope (role, governorateCode, "
            "districtCode), device binding (assignedDeviceId, "
            "publicKeyBase64), lifecycle (isActive, provisionedBy, "
            "provisionedAt, lastLoginAt), and forward-compatible extension "
            "(metadataExtraJson)."
        ),
        "why": [
            "Support complete offline-first user lifecycle for Yemen Water Authority hierarchy",
            "All 16 fields are actively used (no speculative columns)",
            "Wide table performs better than joins for small user count (<1000)",
            "metadataExtraJson allows non-breaking schema evolution",
        ],
        "alternatives": [
            ("Normalized schema (users + user_roles + user_scopes tables)",
             "Over-engineered for offline-first single-user-per-device reality"),
            ("JSON blob for everything",
             "Loses queryability and index support"),
        ],
        "tradeoffs": [
            "Wide table (16 columns) requires careful mapping",
            "All fields loaded even when only some are needed",
        ],
        "revisit": [
            "If user count per device exceeds 5",
            "If role permissions become dynamic (not enum-driven)",
        ],
    },
    {
        "id": "1.2",
        "title": "Indexing Strategy (6 indices)",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/local/entity/UserEntity.kt",
        "what": (
            "Six indices on users table: username (UNIQUE), role, "
            "governorateCode, districtCode, isActive, assignedDeviceId."
        ),
        "why": [
            "username UNIQUE: enforces login uniqueness (queried every login)",
            "role: filter 'all supervisors' (queried in provisioning UI)",
            "governorateCode: scope filtering for governorate supervisors",
            "districtCode: scope filtering for district supervisors",
            "isActive: quickly enumerate active field workers",
            "assignedDeviceId: verify device binding on every login",
        ],
        "alternatives": [
            ("Single composite index",
             "Query patterns are too varied to benefit from one composite"),
            ("No indices (rely on table scan)",
             "Login latency would degrade as user table grows"),
        ],
        "tradeoffs": [
            "~30% storage overhead per index",
            "Slower INSERTs (acceptable — users table is read-heavy)",
        ],
        "revisit": [
            "If user count exceeds 10,000 (unlikely for one authority)",
        ],
    },
    {
        "id": "1.3",
        "title": "Cryptographic PIN Storage & Verification Scheme",
        "phase": "Phase 1",
        "status": "Accepted (revisited and confirmed 2024-12-19)",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/repository/UserRepositoryImpl.kt:298-323",
        "what": (
            "Salted SHA-256 (single round) with 16-byte SecureRandom salt, "
            "Base64 encoding, and constant-time comparison. "
            "PIN length: 4 digits."
        ),
        "why": [
            "Lost/stolen devices are typically factory-reset and sold, not forensically analyzed",
            "Industry precedent: iOS, Android, and banking ATMs use 4-digit PINs with equivalent security",
            "Salted SHA-256 prevents rainbow table attacks (main real-world threat)",
            "Constant-time comparison prevents timing side-channels",
            "Device binding (assignedDeviceId) adds second security layer",
            "Session timeout (30 min) limits exposure window",
        ],
        "alternatives": [
            ("PBKDF2 (100k-600k iterations)",
             "Adds 200-500ms latency per login. Only meaningful vs sophisticated "
             "forensic attacks (unlikely in our threat model). 10,000-PIN keyspace "
             "is small enough that PBKDF2 offers only hours of protection."),
            ("Argon2",
             "No native Android SDK support; adds heavy dependency for marginal gain"),
            ("BCrypt",
             "Same rationale as PBKDF2 rejection"),
        ],
        "tradeoffs": [
            " Sophisticated attacker with physical device access AND DB extraction "
            "capability CAN brute-force PIN in <1 second on modern GPU",
            " This threat model is out of scope for Yemen Water Authority field surveys",
            " Simpler code = fewer bugs = better real-world security",
        ],
        "compensating_controls": [
            "Device binding (assignedDeviceId) — DB from Device A won't work on Device B",
            "Session timeout (30 min inactivity)",
            "Account deactivation capability (isActive flag)",
            "Future: SQLCipher database encryption (Phase 3+)",
        ],
        "revisit": [
            "If threat model changes (devices contain sensitive PII beyond survey data)",
            "If regulatory requirements mandate KDF usage",
            "If PIN length is extended to 6+ digits (then PBKDF2 becomes cost-effective)",
            "If SQLCipher adds performance budget for KDF",
        ],
    },
    {
        "id": "1.4",
        "title": "UserRole 4-Tier Hierarchy",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/domain/model/UserRole.kt",
        "what": (
            "Four-tier enum: ENUMERATOR, DISTRICT_SUPERVISOR, "
            "GOVERNORATE_SUPERVISOR, CENTRAL_SUPERVISOR. Stored as String."
        ),
        "why": [
            "Directly mirrors Yemen Water Authority administrative structure",
            "Each tier has distinct authority scope (nothing → district → governorate → national)",
            "Enum enables exhaustive `when` at compile time",
            "String storage is human-readable in DB dumps",
        ],
        "alternatives": [
            ("Permission-based RBAC",
             "Overkill for 4 well-defined roles"),
            ("Boolean flags (isAdmin, canExport, etc.)",
             "Doesn't express hierarchy naturally"),
            ("Integer storage (ordinal)",
             "Fragile on enum reorder; loses readability"),
        ],
        "tradeoffs": [
            "String storage is ~10 bytes vs 1 byte for int (negligible for <1000 users)",
            "Adding new role requires migration + code change",
        ],
        "revisit": [
            "If organizational structure adds new tier (e.g., regional layer)",
            "If permissions become user-specific (not role-derived)",
        ],
    },
    {
        "id": "1.5",
        "title": "Repository Contract with Flow + suspend + Dispatchers.IO",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/domain/repository/UserRepository.kt",
        "what": (
            "Repository interface uses Flow<T> for observable streams, "
            "suspend for one-shot operations, explicit Dispatchers.IO for "
            "all DB access, Result<T> for expected failures."
        ),
        "why": [
            "Flow integrates natively with Jetpack Compose",
            "suspend enforces structured concurrency (no leaks)",
            "Explicit Dispatchers.IO prevents accidental main-thread DB access",
            "Result<T> makes expected errors part of the type signature",
        ],
        "alternatives": [
            ("LiveData",
             "Legacy; not idiomatic for Compose"),
            ("RxJava",
             "Heavy dependency; obsolete in modern Android"),
            ("Callback-based API",
             "Coroutines are the Android standard since 2019"),
        ],
        "tradeoffs": [
            "Requires all contributors to understand coroutines",
            "Flow debugging can be harder than callbacks",
        ],
        "revisit": [
            "Never (coroutines are the Android standard)",
        ],
    },
    {
        "id": "1.6",
        "title": "AuthenticationResult Sealed Class",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/domain/model/AuthenticationResult.kt",
        "what": (
            "Sealed class with 6 outcomes: Success, InvalidCredentials, "
            "UserNotFound, UserInactive, DeviceMismatch, UnexpectedError. "
            "Each case carries typed data specific to that outcome."
        ),
        "why": [
            "Login has 6 distinct outcomes requiring different UI handling",
            "Sealed class enables exhaustive `when` (compile-time safety)",
            "Typed data per case (e.g., attempt count for InvalidCredentials)",
            "Prevents null-checking anti-pattern",
        ],
        "alternatives": [
            ("Boolean + separate error String",
             "Loses type safety; error strings become magic values"),
            ("Exceptions for failures",
             "Expected failures shouldn't throw (performance + clarity)"),
            ("Result<UserSession>",
             "Can't express 4 different failure types with typed data"),
        ],
        "tradeoffs": [
            "6 classes to maintain (each is small data class)",
            "Adding new outcome requires touching all consumers (good — forces review)",
        ],
        "revisit": [
            "If failure types exceed ~10 (then reconsider design)",
        ],
    },
    {
        "id": "1.7",
        "title": "UserMapper with Credential Isolation",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/mapper/UserMapper.kt",
        "what": (
            "Mapper converts UserEntity ↔ User (domain). Domain User model "
            "OMITS pinSalt and pinSaltedHash fields. Username is normalized "
            "(lowercase, trim) before storage."
        ),
        "why": [
            "Domain User MUST NOT expose credentials to ViewModels/UI",
            "Compile-time enforcement (fields don't exist to leak)",
            "Username normalization prevents duplicate accounts (Ali vs ali vs Ali )",
            "Clean Architecture: domain layer has zero Room imports",
        ],
        "alternatives": [
            ("Expose Entity directly to UI",
             "Security leak risk; couples UI to persistence details"),
            ("@Ignore on domain fields",
             "Room-specific annotation couples domain to Room framework"),
        ],
        "tradeoffs": [
            "Extra mapping code (small, testable)",
            "Two models to maintain (Entity + Domain)",
        ],
        "revisit": [
            "Never (Clean Architecture foundational principle)",
        ],
    },
    {
        "id": "1.8",
        "title": "In-Memory Session with 30-Minute Sliding Timeout",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
             "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/session/SessionManager.kt",
        "what": (
            "Session state held in-memory only (no disk persistence). "
            "30-minute inactivity timeout with sliding renewal on activity. "
            "Process death forces re-authentication."
        ),
        "why": [
            "In-memory: field devices shared between shifts — process kill forces re-auth",
            "30 minutes: balances security (idle auto-logout) vs UX (doesn't interrupt surveys)",
            "Sliding: user activity extends session (doesn't punish long surveys)",
            "No token persistence = no token theft from disk",
        ],
        "alternatives": [
            ("EncryptedSharedPreferences token",
             "Persists across process death (unwanted for shared devices)"),
            ("No timeout (session until logout)",
             "Unattended devices = security risk"),
            ("Fixed 8-hour session",
             "Doesn't account for idle time; wastes security budget"),
        ],
        "tradeoffs": [
            "Process restart = re-login (accepted; rare event)",
            "In-memory state lost on app crash (acceptable for security posture)",
        ],
        "revisit": [
            "If field workers complain about frequent re-authentication",
            "If devices become single-assignment (not shared between shifts)",
        ],
    },
    {
        "id": "1.9",
        "title": "Manual MIGRATION_7_8 with Raw SQL + IF NOT EXISTS",
        "phase": "Phase 1",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/local/migration/Migrations.kt",
        "what": (
            "Manual Room Migration from schema v7 to v8. Uses raw SQL "
            "with 'CREATE TABLE IF NOT EXISTS' and 'CREATE INDEX IF NOT "
            "EXISTS' for idempotency. No foreign key constraints."
        ),
        "why": [
            "Manual: full control over DDL, easy to review in PR",
            "IF NOT EXISTS: idempotent (safe to run twice, safe on partial failures)",
            "No FK constraints: users table is independent; FKs complicate future migrations",
            "Explicit SQL is auditable (unlike generated AutoMigration code)",
        ],
        "alternatives": [
            ("Room AutoMigration",
             "Doesn't support all schema changes; generated code is opaque"),
            ("Destructive migration (fallbackToDestructiveMigration)",
             "v7 databases contain real survey data — unacceptable data loss"),
        ],
        "tradeoffs": [
            "Manual SQL requires careful review (mitigated by MigrationTest suite)",
            "Every schema change requires new migration file",
        ],
        "revisit": [
            "When Room AutoMigration matures for complex scenarios (Room 3.x+)",
        ],
    },
    {
        "id": "2.1",
        "title": "Robolectric-Based Migration Testing",
        "phase": "Phase 2.5",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/test/java/com/yemen/watersurvey/data/local/migration/MigrationTest.kt",
        "what": (
            "Migration tests run via Robolectric on JVM (not instrumented). "
            "Three tests validate: (1) schema correctness after migration, "
            "(2) data preservation from v7, (3) DAO interop on v8 database."
        ),
        "why": [
            "Fast JVM execution (no emulator startup)",
            "Runs in CI without device farm dependency",
            "Real SQLite via Robolectric's shadow implementation",
            "3 tests cover the critical migration failure modes",
        ],
        "alternatives": [
            ("Instrumented tests (androidTest)",
             "Slow; requires device/emulator; harder in CI"),
            ("No migration tests",
             "Too risky for production data"),
        ],
        "tradeoffs": [
            "Robolectric's SQLite ≠ Android device SQLite (rare edge cases)",
            "Accepted risk given speed and CI benefits",
        ],
        "revisit": [
            "If SQLite behavior differences cause production bugs",
        ],
    },
    {
        "id": "2.2",
        "title": "Test Dependencies (Robolectric 4.11.1 + Room-testing 2.6.1)",
        "phase": "Phase 2.5",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/build.gradle.kts",
        "what": (
            "Added testImplementation dependencies: Robolectric 4.11.1, "
            "androidx.room:room-testing:2.6.1, kotlinx-coroutines-test."
        ),
        "why": [
            "Versions matched to existing project (Room 2.6.1 in main deps)",
            "Latest stable releases with active maintenance",
            "Official Google/Robolectric support",
        ],
        "alternatives": [
            ("Older Robolectric versions",
             "Known SQLite compatibility issues"),
        ],
        "tradeoffs": [
            "Adds ~15MB to test classpath (acceptable)",
        ],
        "revisit": [
            "Yearly, when new stable versions release",
        ],
    },
    {
        "id": "3.1",
        "title": "Schema Export to Git-Tracked JSON",
        "phase": "Phase 2.6",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/schemas/com.yemen.watersurvey.data.local.WaterSurveyDatabase/8.json",
        "what": (
            "Room configured to export schema JSON to app/schemas/. "
            "Files are Git-tracked. Directory structure: "
            "schemas/{database_class}/{version}.json"
        ),
        "why": [
            "schemas/8.json is source of truth for v8 database structure",
            "Git-tracked = code review catches accidental schema changes",
            "Enables automated migration validation",
            "Identity hash detects unintended entity modifications",
        ],
        "alternatives": [
            (".gitignore schemas/",
             "Loses migration safety net; no historical record"),
            ("Manual schema documentation",
             "Drifts from reality over time"),
        ],
        "tradeoffs": [
            "Every schema change requires committing generated JSON",
            "Merge conflicts possible on parallel schema changes (rare)",
        ],
        "revisit": [
            "Never (Room best practice)",
        ],
    },
    {
        "id": "4.1",
        "title": "Clean Architecture (Domain vs Data Separation)",
        "phase": "Cross-Cutting",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/{domain,data}/",
        "what": (
            "Two-layer separation: 'domain' package has zero Android/Room "
            "imports (pure Kotlin). 'data' package handles all persistence "
            "details (Room entities, DAOs, mappers)."
        ),
        "why": [
            "Domain layer testable in pure JVM (no Android runtime)",
            "Persistence details can change without touching business logic",
            "Matches existing project structure (FormPackage, SurveyRecord)",
            "Enforces separation of concerns at package level",
        ],
        "alternatives": [
            ("Single package (no separation)",
             "Mixes concerns; harder to test; couples UI to Room"),
            ("MVVM without domain layer",
             "Business logic leaks into ViewModels"),
        ],
        "tradeoffs": [
            "More files to maintain",
            "Requires mappers (already justified in Decision 1.7)",
        ],
        "revisit": [
            "Never (foundational architecture)",
        ],
    },
    {
        "id": "4.2",
        "title": "Naming Conventions (*Entity, *Dao, *Repository, *Impl)",
        "phase": "Cross-Cutting",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "Repository-wide convention",
        "what": (
            "Suffixes indicate role: UserEntity (Room), UserDao (Room), "
            "UserRepository (interface), UserRepositoryImpl (concrete). "
            "Domain models use plain names (User, UserRole)."
        ),
        "why": [
            "Matches existing project code (FormPackageEntity, SurveyRecordDao)",
            "Instantly recognizable role from name alone",
            "*Impl suffix distinguishes concrete from interface",
            "Domain models have no framework leak in naming",
        ],
        "alternatives": [
            ("No suffixes (User in both layers)",
             "Name collision; import confusion"),
            ("Prefixes (EntityUser, DaoUser)",
             "Non-idiomatic Kotlin"),
        ],
        "tradeoffs": [
            "Slightly longer names (acceptable for clarity)",
        ],
        "revisit": [
            "Never (project-wide convention)",
        ],
    },
    {
        "id": "4.3",
        "title": "Kotlin Coroutines + Flow (No LiveData/RxJava)",
        "phase": "Cross-Cutting",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "All Repository and ViewModel classes",
        "what": (
            "All async operations use Kotlin Coroutines. Observable "
            "streams use Flow. No LiveData or RxJava anywhere in codebase."
        ),
        "why": [
            "Native Jetpack Compose integration",
            "Structured concurrency prevents leaks",
            "Flow for streams, suspend for one-shots (clean split)",
            "Google-recommended Android standard since 2020",
        ],
        "alternatives": [
            ("LiveData",
             "Legacy; suboptimal for Compose"),
            ("RxJava",
             "Heavy; obsolete for new Android projects"),
        ],
        "tradeoffs": [
            "Requires coroutines knowledge (already accepted)",
        ],
        "revisit": [
            "Never",
        ],
    },
    {
        "id": "4.4",
        "title": "Device Hardware Binding (assignedDeviceId)",
        "phase": "Cross-Cutting",
        "status": "Accepted",
        "date": "2024-12-19",
        "evidence": "android_app/app/src/main/java/com/yemen/watersurvey/data/repository/UserRepositoryImpl.kt",
        "what": (
            "Each user record stores assignedDeviceId. Login validates "
            "current device ID matches stored value. First login on "
            "unbound account performs zero-touch bind. Admin can unbind "
            "for legitimate device replacement."
        ),
        "why": [
            "Field devices assigned 1:1 to enumerators",
            "Prevents stolen credentials from working on attacker's device",
            "Zero-touch first bind: convenient for initial deployment",
            "Defense in depth: complements PIN security (Decision 1.3)",
        ],
        "alternatives": [
            ("No device binding",
             "PIN alone is insufficient (credentials can be shared/stolen)"),
            ("Hardware attestation (SafetyNet/Play Integrity)",
             "Requires Play Services (not available in all field regions)"),
            ("Certificate-based device auth",
             "Overkill for Phase 1; adds provisioning complexity"),
        ],
        "tradeoffs": [
            "Device replacement requires admin action (rare event, acceptable)",
            "Zero-touch first bind = brief window of trust on initial login",
        ],
        "revisit": [
            "If certificate-based device identity becomes feasible in Phase 3+",
            "If Play Services becomes reliably available across deployment regions",
        ],
    },
]

# ==============================================================================
# MARKDOWN GENERATION
# ==============================================================================

def slugify(text: str) -> str:
    """Convert decision title to markdown anchor."""
    return text.lower().replace(" ", "-").replace(",", "").replace("(", "").replace(")", "").replace("/", "").replace("&", "").replace("+", "").replace(":", "").replace("*", "").replace(".", "")


def generate_header() -> str:
    return f"""#  Architectural Decisions Record — {PROJECT_NAME}

**Version**: {PROJECT_VERSION}  
**Commit Reference**: `{COMMIT_REF}`  
**Generated**: {GENERATED_DATE}  
**Total Decisions**: {len(DECISIONS)}

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

"""


def generate_toc() -> str:
    lines = ["## Table of Contents\n"]

    # Group by phase
    phases = {}
    for d in DECISIONS:
        phases.setdefault(d["phase"], []).append(d)
    
    for phase in ["Phase 1", "Phase 2.5", "Phase 2.6", "Cross-Cutting"]:
        if phase not in phases:
            continue
        lines.append(f"\n### {phase}\n")
        for d in phases[phase]:
            anchor = f"decision-{d['id'].replace('.', '')}-{slugify(d['title'])}"
            lines.append(f"- [Decision {d['id']}: {d['title']}](#{anchor})")
    
    lines.append("\n\n---\n")
    return "\n".join(lines)


def generate_decision(d: dict) -> str:
    """Generate markdown for a single decision."""
    anchor_title = d["title"]
    
    md = f"\n## Decision {d['id']}: {anchor_title}\n\n"
    md += f"**Status**: {d['status']}  \n"
    md += f"**Date**: {d['date']}  \n"
    md += f"**Phase**: {d['phase']}  \n"
    md += f"**Evidence**: `{d['evidence']}`\n\n"
    
    # What
    md += "### What\n\n"
    md += f"{d['what']}\n\n"
    
    # Why
    md += "### Why\n\n"
    for reason in d["why"]:
        md += f"- {reason}\n"
    md += "\n"
    
    # Alternatives
    md += "### Alternatives Considered\n\n"
    for alt, rejection in d["alternatives"]:
        md += f"- **{alt}**  \n"
        md += f"  _Rejected because_: {rejection}\n"
    md += "\n"
    
    # Trade-offs
    md += "### Trade-offs Accepted\n\n"
    for tradeoff in d["tradeoffs"]:
        md += f"- {tradeoff}\n"
    md += "\n"
    
    # Compensating Controls (only if present)
    if "compensating_controls" in d:
        md += "### Compensating Controls\n\n"
        for control in d["compensating_controls"]:
            md += f"- {control}\n"
        md += "\n"
    
    # Revisit
    md += "### Revisit Trigger\n\n"
    for trigger in d["revisit"]:
        md += f"- {trigger}\n"
    md += "\n"
    
    md += "---\n"
    return md


def generate_footer() -> str:
    return f"""
## 📌 Adding New Decisions

To document a new architectural decision:

1. Open `generate_decisions.py`
2. Append a new dict to the `DECISIONS` list following this template:

```python
{{
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
}},
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

_Generated by `generate_decisions.py` on {GENERATED_DATE}_
_Do not edit this file directly — modify the script and regenerate_
"""


# ==============================================================================
# MAIN
# ==============================================================================

def main():
    output = generate_header()
    output += generate_toc()
    
    # Group decisions by phase for output
    phases = {}
    for d in DECISIONS:
        phases.setdefault(d["phase"], []).append(d)
    
    for phase in ["Phase 1", "Phase 2.5", "Phase 2.6", "Cross-Cutting"]:
        if phase not in phases:
            continue
        output += f"\n# {phase}\n"
        for d in phases[phase]:
            output += generate_decision(d)
    
    output += generate_footer()
    
    # Write to file
    script_dir = Path(__file__).parent
    output_path = script_dir / "DECISIONS.md"
    
    output_path.write_text(output, encoding="utf-8", newline="\n")
    
    # Report
    line_count = output.count("\n")
    size_kb = len(output.encode("utf-8")) / 1024
    
    print("=" * 60)
    print(" DECISIONS.md generated successfully")
    print("=" * 60)
    print(f" Path:      {output_path}")
    print(f" Decisions: {len(DECISIONS)}")
    print(f" Lines:     {line_count}")
    print(f" Size:      {size_kb:.1f} KB")
    print("=" * 60)
    print("\n Next steps:")
    print("  1. Review DECISIONS.md in your editor")
    print("  2. Verify all 16 decisions render correctly")
    print("  3. Commit: git add DECISIONS.md generate_decisions.py")
    print("  4. Commit message: docs: add architectural decisions record")


if __name__ == "__main__":
    main()



