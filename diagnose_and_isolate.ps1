# ============================================================
# Contract Discovery Verification Script
# Read-only diagnosis + safe isolation of doc files
# ============================================================

$ErrorActionPreference = "Continue"
$ProjectRoot = "D:\Dev\Project Yemen Water Survey_v02"
Set-Location $ProjectRoot

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  PHASE 1: DIAGNOSIS (read-only)" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# --- Check the 4 doc files ---
Write-Host "[1] Checking existence of agent's 4 files:" -ForegroundColor Yellow
$docs = @(
    "docs\contracts\METADATA_CONTRACT.md",
    "docs\contracts\XLSFORM_CONTRACT.md",
    "_validation_reports\contract_gap_analysis_20260909.md",
    "_validation_reports\contract_implementation_backlog_20260909.md"
)
$allDocsExist = $true
foreach ($doc in $docs) {
    if (Test-Path $doc) {
        $size = (Get-Item $doc).Length
        Write-Host "  [OK] Found ($size bytes): $doc" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING]: $doc" -ForegroundColor Red
        $allDocsExist = $false
    }
}
Write-Host ""

# --- Timestamps of doc files ---
Write-Host "[2] Timestamps of agent's docs:" -ForegroundColor Yellow
foreach ($doc in $docs) {
    if (Test-Path $doc) {
        $item = Get-Item $doc
        Write-Host "  File: $($item.Name)"
        Write-Host "    Created:  $($item.CreationTime)"
        Write-Host "    Modified: $($item.LastWriteTime)"
    }
}
Write-Host ""

# --- Timestamps of suspicious UI files ---
Write-Host "[3] Timestamps of UI files (agent must NOT touch these):" -ForegroundColor Yellow
$suspects = @(
    "android_app\app\src\main\java\com\yemen\watersurvey\MainActivity.kt",
    "android_app\app\src\main\java\com\yemen\watersurvey\presentation\screens\RecordsManagerScreen.kt",
    "android_app\app\src\main\java\com\yemen\watersurvey\presentation\screens\NewDamSurveyScreen.kt",
    "android_app\app\src\main\java\com\yemen\watersurvey\core\sync\SurveySyncExporter.kt"
)
foreach ($file in $suspects) {
    if (Test-Path $file) {
        $item = Get-Item $file
        Write-Host "  File: $($item.Name)"
        Write-Host "    Modified: $($item.LastWriteTime)"
    }
}
Write-Host ""

# --- Check forms files on disk ---
Write-Host "[4] Checking forms files (git thinks they are deleted):" -ForegroundColor Yellow
$formsFiles = @(
    "forms\yem_admin_pcodes-02122024.xlsx",
    "forms\yem_water_wells.xlsx",
    "forms\yem_water_springs.xlsx",
    "forms\yem_water_harvesting.xlsx"
)
foreach ($file in $formsFiles) {
    if (Test-Path $file) {
        Write-Host "  [OK] On disk: $file" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING from disk]: $file" -ForegroundColor Red
    }
}
Write-Host ""

Write-Host "[5] Current contents of forms folder:" -ForegroundColor Yellow
if (Test-Path "forms") {
    Get-ChildItem "forms\" | Format-Table Name, LastWriteTime, Length -AutoSize
} else {
    Write-Host "  [ERROR] forms folder does not exist!" -ForegroundColor Red
}
Write-Host ""

# ============================================================
# Auto analysis
# ============================================================
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  PHASE 2: AUTO ANALYSIS" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

if ((Test-Path $docs[0]) -and (Test-Path $suspects[1])) {
    $docTime = (Get-Item $docs[0]).LastWriteTime
    $uiTime = (Get-Item $suspects[1]).LastWriteTime
    $diffMinutes = [Math]::Abs(($docTime - $uiTime).TotalMinutes)
    
    Write-Host "Time gap between doc file and UI file edits:" -ForegroundColor Yellow
    Write-Host "  $([Math]::Round($diffMinutes, 1)) minutes"
    
    if ($diffMinutes -lt 30) {
        Write-Host "  [RED FLAG] Very short gap - agent likely modified UI files" -ForegroundColor Red
    } elseif ($diffMinutes -lt 120) {
        Write-Host "  [WARNING] Medium gap - needs manual verification" -ForegroundColor Yellow
    } else {
        Write-Host "  [OK] Large gap - agent likely innocent" -ForegroundColor Green
    }
}
Write-Host ""

# ============================================================
# Isolation prompt
# ============================================================
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  PHASE 3: ISOLATE DOCS IN OWN COMMIT" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

if (-not $allDocsExist) {
    Write-Host "[ABORT] Some docs missing - cannot isolate!" -ForegroundColor Red
    Write-Host "Send the output above for discussion." -ForegroundColor Yellow
    exit
}

Write-Host "All 4 docs exist and are ready to isolate." -ForegroundColor Green
Write-Host ""
Write-Host "This will do:" -ForegroundColor Yellow
Write-Host "  1. git add for the 4 doc files ONLY"
Write-Host "  2. git status for verification"
Write-Host "  3. git commit with docs only"
Write-Host ""
Write-Host "This will NOT touch:" -ForegroundColor Yellow
Write-Host "  - Any modified .kt file"
Write-Host "  - Any deleted forms file"
Write-Host "  - .vscode/settings.json"
Write-Host ""

$confirmation = Read-Host "Continue? Type YES exactly"

if ($confirmation -ne "YES") {
    Write-Host ""
    Write-Host "[CANCELLED] Nothing changed." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Starting isolation..." -ForegroundColor Green
Write-Host ""

foreach ($doc in $docs) {
    Write-Host "  + $doc"
    git add $doc
}

Write-Host ""
Write-Host "Git status after add (must show 4 files under 'Changes to be committed'):" -ForegroundColor Yellow
git status --short
Write-Host ""

$finalConfirm = Read-Host "Does the above show only 4 new files with 'A '? Type YES to commit"

if ($finalConfirm -ne "YES") {
    Write-Host ""
    Write-Host "Cancelling commit - reverting git add..." -ForegroundColor Yellow
    git reset HEAD
    Write-Host "[OK] Reverted. Nothing changed." -ForegroundColor Green
    exit
}

git commit -m "docs(contracts): add metadata and XLSForm contract discovery + gap analysis"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  [SUCCESS] Isolation complete" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Last 3 commits:" -ForegroundColor Yellow
git log --oneline -3
Write-Host ""
Write-Host "Remaining changes still in working directory (untouched):" -ForegroundColor Yellow
git status --short
Write-Host ""
Write-Host "[OK] Docs are safe in history now." -ForegroundColor Green
Write-Host "Next step: decide fate of the other 22 changed files calmly." -ForegroundColor Cyan
