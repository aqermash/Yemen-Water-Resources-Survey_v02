$ErrorActionPreference = "Continue"
$ProjectRoot = "D:\Dev\Project Yemen Water Survey_v02"
$OutputDir = Join-Path $ProjectRoot "_login_audit\output"

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$ReportFile = Join-Path $OutputDir "_login_audit_report.md"
$FilesListFile = Join-Path $OutputDir "files_found.txt"
$SnippetsFile = Join-Path $OutputDir "code_snippets.md"

Write-Host ""
Write-Host "=== Login System Audit ===" -ForegroundColor Cyan
Write-Host "Project: $ProjectRoot" -ForegroundColor Yellow
Write-Host ""

$SearchKeywords = @("Login","Auth","SignIn","SignUp","Splash","Activation","User","Session","Token","Credential","Password","Supervisor","Surveyor","Role","Permission","Preferences","SharedPref","DataStore","Keystore","Encrypt","Decrypt","Hash")

$CodeExtensions = @("*.kt","*.java","*.xml")
$ExcludePaths = @("build",".gradle",".idea","_login_audit","backup")

Write-Host "[1/5] Scanning files..." -ForegroundColor Green
$AllCodeFiles = @()
foreach ($ext in $CodeExtensions) {
    $files = Get-ChildItem -Path $ProjectRoot -Recurse -Filter $ext -ErrorAction SilentlyContinue | Where-Object {
        $path = $_.FullName
        $excluded = $false
        foreach ($excl in $ExcludePaths) {
            if ($path -like "*\$excl\*") { $excluded = $true; break }
        }
        -not $excluded
    }
    $AllCodeFiles += $files
}
Write-Host "   Found $($AllCodeFiles.Count) files" -ForegroundColor Gray

Write-Host "[2/5] Searching content..." -ForegroundColor Green
$AuthFilesByContent = @{}
$KeywordHits = @{}
foreach ($kw in $SearchKeywords) { $KeywordHits[$kw] = 0 }

foreach ($file in $AllCodeFiles) {
    try {
        $content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($null -eq $content) { continue }
        $matched = @()
        foreach ($kw in $SearchKeywords) {
            if ($content -match "\b$kw\b") {
                $matched += $kw
                $KeywordHits[$kw]++
            }
        }
        if ($matched.Count -gt 0) {
            $AuthFilesByContent[$file.FullName] = $matched
        }
    } catch {}
}
Write-Host "   Auth files: $($AuthFilesByContent.Count)" -ForegroundColor Gray

Write-Host "[3/5] Extracting snippets..." -ForegroundColor Green
$TopAuthFiles = $AuthFilesByContent.GetEnumerator() | Sort-Object { $_.Value.Count } -Descending | Select-Object -First 20
$Snippets = @()
foreach ($entry in $TopAuthFiles) {
    $filePath = $entry.Key
    $keywords = $entry.Value
    $relPath = $filePath.Replace($ProjectRoot, "").TrimStart([char]92)
    try {
        $lines = Get-Content -Path $filePath -ErrorAction SilentlyContinue
        $totalLines = $lines.Count
        $sampleLines = if ($totalLines -le 80) { $lines } else { $lines[0..79] }
        $Snippets += [PSCustomObject]@{
            RelativePath = $relPath
            TotalLines = $totalLines
            Keywords = $keywords -join ", "
            KeywordCount = $keywords.Count
            Sample = $sampleLines -join "`n"
        }
    } catch {}
}
Write-Host "   Extracted $($Snippets.Count) snippets" -ForegroundColor Gray

Write-Host "[4/5] Detecting patterns..." -ForegroundColor Green
$PatternRegex = @{}
$PatternRegex["SharedPreferences"] = "SharedPreferences|getSharedPreferences"
$PatternRegex["DataStore"] = "DataStore|preferencesDataStore"
$PatternRegex["Room"] = "Entity|Dao|RoomDatabase"
$PatternRegex["Retrofit"] = "Retrofit"
$PatternRegex["Firebase"] = "FirebaseAuth"
$PatternRegex["Encryption"] = "Cipher|EncryptedSharedPreferences"
$PatternRegex["Hashing"] = "MessageDigest|SHA-256|BCrypt"
$PatternRegex["Keystore"] = "AndroidKeyStore"
$PatternRegex["Navigation"] = "NavController"
$PatternRegex["Compose"] = "Composable"
$PatternRegex["ViewBinding"] = "ViewBinding"
$PatternRegex["Hilt"] = "HiltAndroidApp|Inject"
$PatternRegex["Coroutines"] = "suspend fun|CoroutineScope"

$Patterns = @{}
foreach ($key in $PatternRegex.Keys) { $Patterns[$key] = 0 }
foreach ($file in $AllCodeFiles) {
    try {
        $content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($null -eq $content) { continue }
        foreach ($pattern in $PatternRegex.Keys) {
            if ($content -match $PatternRegex[$pattern]) {
                $Patterns[$pattern]++
            }
        }
    } catch {}
}
Write-Host "   Done" -ForegroundColor Gray

Write-Host "[5/5] Writing reports..." -ForegroundColor Cyan

$FilesList = @()
$FilesList += "AUTH-RELATED FILES"
$FilesList += "Generated: $(Get-Date)"
$FilesList += "Total: $($AuthFilesByContent.Count)"
$FilesList += ""
$sorted = $AuthFilesByContent.GetEnumerator() | Sort-Object { $_.Value.Count } -Descending
foreach ($entry in $sorted) {
    $rel = $entry.Key.Replace($ProjectRoot, "").TrimStart([char]92)
    $FilesList += "[$($entry.Value.Count)] $rel"
    $FilesList += "   Keywords: $($entry.Value -join ', ')"
}
$FilesList | Out-File -FilePath $FilesListFile -Encoding UTF8

$SnippetsOut = @()
$SnippetsOut += "# Code Snippets"
$SnippetsOut += ""
foreach ($snip in $Snippets) {
    $SnippetsOut += "## $($snip.RelativePath)"
    $SnippetsOut += "- Lines: $($snip.TotalLines)"
    $SnippetsOut += "- Keywords: $($snip.Keywords)"
    $SnippetsOut += ""
    $SnippetsOut += "``````"
    $SnippetsOut += $snip.Sample
    $SnippetsOut += "``````"
    $SnippetsOut += ""
}
$SnippetsOut | Out-File -FilePath $SnippetsFile -Encoding UTF8

$Report = @()
$Report += "# Login Audit Report"
$Report += "Generated: $(Get-Date)"
$Report += ""
$Report += "## Summary"
$Report += "- Total files: $($AllCodeFiles.Count)"
$Report += "- Auth files: $($AuthFilesByContent.Count)"
$Report += "- Snippets: $($Snippets.Count)"
$Report += ""
$Report += "## Keywords"
$sortedKw = $KeywordHits.GetEnumerator() | Sort-Object Value -Descending
foreach ($kw in $sortedKw) {
    if ($kw.Value -gt 0) { $Report += "- $($kw.Key): $($kw.Value)" }
}
$Report += ""
$Report += "## Patterns"
$sortedPt = $Patterns.GetEnumerator() | Sort-Object Value -Descending
foreach ($p in $sortedPt) {
    $Report += "- $($p.Key): $($p.Value)"
}
$Report += ""
$Report += "## Top Files"
$i = 1
foreach ($snip in $Snippets) {
    $Report += "$i. $($snip.RelativePath) [$($snip.KeywordCount) keywords]"
    $i++
}
$Report | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host ""
Write-Host "=== COMPLETE ===" -ForegroundColor Green
Write-Host "Files scanned: $($AllCodeFiles.Count)" -ForegroundColor White
Write-Host "Auth files: $($AuthFilesByContent.Count)" -ForegroundColor White
Write-Host "Output: $OutputDir" -ForegroundColor Yellow
