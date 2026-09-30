Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Format only staged Java files with Spotless before commit.
$repoRoot = (git rev-parse --show-toplevel).Trim()
$stagedFiles = @(git diff --cached --name-only --diff-filter=ACMR -- '*.java')

if ($stagedFiles.Count -eq 0) {
    exit 0
}

# spotless-maven-plugin requires absolute paths for -DspotlessFiles
$spotlessFiles = ($stagedFiles | ForEach-Object { "$repoRoot/$_" }) -join ','

if (Test-Path '.\mvnw.cmd') {
    & .\mvnw.cmd -q spotless:apply "-DspotlessFiles=$spotlessFiles"
} elseif (Test-Path '.\mvnw') {
    & .\mvnw -q spotless:apply "-DspotlessFiles=$spotlessFiles"
} else {
    Write-Error 'mvnw/mvnw.cmd not found. Cannot run Spotless.'
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

foreach ($file in $stagedFiles) {
    if (Test-Path $file) {
        git add -- $file
    }
}

# Abort if Spotless reverted all staged changes back to HEAD (e.g. only
# formatting violations were staged and nothing new remains to commit).
$remainingDiff = git diff --cached --name-only
if (-not $remainingDiff) {
    Write-Host 'Pre-commit: Spotless corrected all staged Java files back to their' -ForegroundColor Yellow
    Write-Host 'HEAD state. Nothing left to commit — aborting.' -ForegroundColor Yellow
    Write-Host 'Tip: re-stage your changes after reviewing the Spotless corrections.' -ForegroundColor Yellow
    exit 1
}
