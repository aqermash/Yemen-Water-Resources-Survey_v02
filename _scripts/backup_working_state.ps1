# ============================================================
# Safety Backup Script
# Saves current working directory into a backup branch
# Then returns master to clean state
# ============================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = "D:\Dev\Project Yemen Water Survey_v02"
Set-Location $ProjectRoot

$BackupBranch = "backup/pre-cleanup-20260909"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  SAFETY BACKUP - Snapshot working directory" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# --- Show current state ---
Write-Host "[1] Current branch:" -ForegroundColor Yellow
git branch --show-current
Write-Host ""

Write-Host "[2] Current HEAD commit:" -ForegroundColor Yellow
git log --oneline -1
Write-Host ""

Write-Host "[3] Number of files to be backed up:" -ForegroundColor Yellow
$modifiedCount = (git status --porcelain | Measure-Object).Count
Write-Host "  Total: $modifiedCount files (modified/deleted/new)"
Write-Host ""

# --- Check if backup branch already exists ---
$existingBranch = git branch --list $BackupBranch
if ($existingBranch) {
    Write-Host "[WARNING] Backup branch already exists: $BackupBranch" -ForegroundColor Red
    Write-Host "  Delete it first or choose different name." -ForegroundColor Yellow
    exit
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  PLAN OF ACTION" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will:" -ForegroundColor Yellow
Write-Host "  1. Create new branch: $BackupBranch (from current master)"
Write-Host "  2. Switch to the new branch"
Write-Host "  3. Add ALL current changes (git add -A)"
Write-Host "  4. Commit them with a clear backup message"
Write-Host "  5. Switch BACK to master"
Write-Host ""
Write-Host "Result after script:" -ForegroundColor Yellow
Write-Host "  - master: unchanged, clean (only docs commit)"
Write-Host "  - $BackupBranch : contains all your WIP safely"
Write-Host ""
Write-Host "Nothing is lost. If we need it back later:" -ForegroundColor Yellow
Write-Host "  git checkout $BackupBranch"
Write-Host ""

$confirm1 = Read-Host "Continue? Type YES exactly"

if ($confirm1 -ne "YES") {
    Write-Host ""
    Write-Host "[CANCELLED] Nothing changed." -ForegroundColor Red
    exit
}

# --- Execute ---
Write-Host ""
Write-Host "Step 1: Creating and switching to backup branch..." -ForegroundColor Green
git checkout -b $BackupBranch

Write-Host ""
Write-Host "Step 2: Staging ALL changes..." -ForegroundColor Green
git add -A

Write-Host ""
Write-Host "Step 3: Verifying what will be committed..." -ForegroundColor Yellow
git status --short
Write-Host ""

$stagedCount = (git diff --cached --name-only | Measure-Object).Count
Write-Host "Files ready to commit: $stagedCount" -ForegroundColor Yellow
Write-Host ""

$confirm2 = Read-Host "Proceed with backup commit? Type YES exactly"

if ($confirm2 -ne "YES") {
    Write-Host ""
    Write-Host "Reverting - unstaging files and returning to master..." -ForegroundColor Yellow
    git reset HEAD
    git checkout master
    git branch -D $BackupBranch
    Write-Host "[OK] Everything reverted. State is exactly as before." -ForegroundColor Green
    exit
}

Write-Host ""
Write-Host "Step 4: Committing backup..." -ForegroundColor Green
git commit -m "chore(backup): snapshot before cleanup - contains WIP with dam form issue

Contains:
- v7 forms upgrade (user work, 7/9)
- Agent work: FieldPackageManager, SurveyPreviewScreen, UI edits
- Unknown: _c5_inspection folder
- Known issue: dam survey form not showing, using mock data
- Deleted legacy forms and PDFs

Purpose: safety net before cleanup investigation."

Write-Host ""
Write-Host "Step 5: Returning to master..." -ForegroundColor Green
git checkout master

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  [SUCCESS] Backup complete" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Current branch:" -ForegroundColor Yellow
git branch --show-current
Write-Host ""
Write-Host "Master status (should be clean):" -ForegroundColor Yellow
git status --short
Write-Host ""
Write-Host "All branches:" -ForegroundColor Yellow
git branch
Write-Host ""
Write-Host "Recent commits on master:" -ForegroundColor Yellow
git log --oneline -5
Write-Host ""
Write-Host "[OK] Master is clean. Backup is safe in branch: $BackupBranch" -ForegroundColor Green
Write-Host "Next: we investigate _c5_inspection and agent code calmly." -ForegroundColor Cyan
