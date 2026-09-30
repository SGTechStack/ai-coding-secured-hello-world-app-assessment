# Build and start the Hello World Auth app (backend :8080 + frontend :3000).
#
#   .\start.ps1                    # build both, then run backend (dev profile) + Vite dev server
#   .\start.ps1 -SpringProfile prod   # run the backend under the prod profile
#   .\start.ps1 -SkipBuild         # skip the build steps, just start
#
# Maven behind a TLS-inspecting proxy (Zscaler etc.):
#   $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"; .\start.ps1
[CmdletBinding()]
param(
    # NOTE: do NOT name this parameter "Profile" — $Profile is a PowerShell
    # automatic variable (path to the user profile script), so a param named
    # Profile shadows it and behaves unpredictably.
    [string]$SpringProfile = 'dev',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Resolve tool executables up front with clear errors instead of cryptic
# "not recognized" failures mid-run.
function Assert-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command '$name' was not found on PATH."
    }
}
Assert-Command java
if (-not $SkipBuild) {
    Assert-Command mvn
    Assert-Command npm
}

if (-not $SkipBuild) {
    Write-Host '==> Building backend (mvn package, tests skipped)'
    Push-Location backend
    try {
        # mvn on Windows is mvn.cmd; call through cmd so a non-zero exit is seen.
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Backend build failed (mvn exit $LASTEXITCODE)." }
    } finally { Pop-Location }

    Write-Host '==> Building frontend (npm ci + vite build)'
    Push-Location frontend
    try {
        & npm ci --no-fund --no-audit
        if ($LASTEXITCODE -ne 0) { throw "npm ci failed (exit $LASTEXITCODE)." }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}

# Resolve the built jar dynamically so a version bump doesn't break the path.
$jar = Get-ChildItem -Path (Join-Path $PSScriptRoot 'backend\target') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(-sources|-javadoc|\.original)$' -and $_.Name -like 'hello-auth-backend-*.jar' } |
    Select-Object -First 1
if (-not $jar) {
    throw "Backend jar not found in backend\target. Run without -SkipBuild first, or run 'mvn -DskipTests package' in backend."
}

$backend = Start-Process -PassThru -NoNewWindow java `
    -ArgumentList '-jar', $jar.FullName, "--spring.profiles.active=$SpringProfile"
Write-Host "==> Started backend on :8080 (profile: $SpringProfile, pid $($backend.Id))"

# npm is a .cmd shim — go through cmd.exe so the process tree is killable.
$frontend = Start-Process -PassThru -NoNewWindow cmd.exe `
    -ArgumentList '/c', 'npm', 'run', 'dev' `
    -WorkingDirectory (Join-Path $PSScriptRoot 'frontend')
Write-Host "==> Started frontend on :3000 (vite dev server, pid $($frontend.Id))"

Write-Host '==> Both starting. SPA: http://localhost:3000  API: http://localhost:8080'
Write-Host '    dev admin login: admin / admin-local-dev-password (dev profile only)'

try {
    Wait-Process -Id $backend.Id, $frontend.Id
} finally {
    # /T kills the whole tree so cmd.exe -> npm -> node doesn't orphan vite.
    foreach ($p in $backend, $frontend) {
        if ($p -and -not $p.HasExited) {
            taskkill /T /F /PID $p.Id | Out-Null
        }
    }
}
