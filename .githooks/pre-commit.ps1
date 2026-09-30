Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (git rev-parse --show-toplevel).Trim()

# --- Backend: Spotless (Java) ---

$stagedJava = @(git diff --cached --name-only --diff-filter=ACMR -- '*.java')

if ($stagedJava.Count -gt 0) {
    $spotlessFiles = ($stagedJava | ForEach-Object { "$repoRoot/$_" }) -join ','

    if (Test-Path "$repoRoot\backend\mvnw.cmd") {
        & "$repoRoot\backend\mvnw.cmd" -f "$repoRoot\backend\pom.xml" -q spotless:apply "-DspotlessFiles=$spotlessFiles"
    } elseif (Test-Path "$repoRoot\backend\mvnw") {
        & "$repoRoot\backend\mvnw" -f "$repoRoot\backend\pom.xml" -q spotless:apply "-DspotlessFiles=$spotlessFiles"
    } else {
        Write-Error 'backend/mvnw not found. Cannot run Spotless.'
    }

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    foreach ($file in $stagedJava) {
        if (Test-Path "$repoRoot\$file") {
            git add -- $file
        }
    }

    $remainingDiff = git diff --cached --name-only
    if (-not $remainingDiff) {
        Write-Host 'Pre-commit: Spotless corrected all staged Java files back to their' -ForegroundColor Yellow
        Write-Host 'HEAD state. Nothing left to commit — aborting.' -ForegroundColor Yellow
        Write-Host 'Tip: re-stage your changes after reviewing the Spotless corrections.' -ForegroundColor Yellow
        exit 1
    }
}

# --- Frontend: lint-staged (Prettier) ---

$stagedFrontend = @(git diff --cached --name-only --diff-filter=ACMR -- '*.ts' '*.tsx' '*.js' '*.json' '*.css' '*.md') | Where-Object { $_ -match '^frontend/' }

if ($stagedFrontend.Count -gt 0) {
    Push-Location "$repoRoot\frontend"
    try {
        npx lint-staged
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}
