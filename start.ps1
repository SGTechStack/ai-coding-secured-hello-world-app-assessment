# Build and start the Hello World Auth app (backend :8080 + frontend :3000).
#
#   .\start.ps1                    # build both, then run backend (dev profile) + Vite dev server
#   .\start.ps1 -Profile prod      # run the backend under the prod profile
#   .\start.ps1 -SkipBuild         # skip the build steps, just start
#
# Maven behind a TLS-inspecting proxy (Zscaler etc.):
#   $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"; .\start.ps1
[CmdletBinding()]
param(
    [string]$Profile = 'dev',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $SkipBuild) {
    Write-Host '==> Building backend (mvn package, tests skipped)'
    Push-Location backend
    try { mvn -q -DskipTests package } finally { Pop-Location }

    Write-Host '==> Building frontend (npm ci + vite build)'
    Push-Location frontend
    try {
        npm ci --no-fund --no-audit
        npm run build
    } finally { Pop-Location }
}

$backend = Start-Process -PassThru -NoNewWindow java `
    -ArgumentList '-jar', 'backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar', "--spring.profiles.active=$Profile"
Write-Host "==> Started backend on :8080 (profile: $Profile, pid $($backend.Id))"

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
