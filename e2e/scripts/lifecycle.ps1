<#
.SYNOPSIS
  Harness-owned lifecycle for the Hello World Auth app (backend + Vite).

.DESCRIPTION
  Implements the E2E reset/isolation contract. The dev backend uses in-memory H2
  (ddl-auto: create-drop) with no HTTP data-reset endpoint, so the only reset
  primitive is a backend process restart, which drops+recreates the schema and
  re-runs the AdminSeeder. This script models isolation as a uniquely identified
  lifecycle:

    up    -LifecycleId <id> [-BackendPort n] [-FrontendPort m] [-SkipBuild]
    down  -LifecycleId <id>
    reset -LifecycleId <id> [ports...]   # down + up: a full data reset
    status -LifecycleId <id>

  Contract guarantees:
    * Starts a uniquely identified lifecycle and prints its id + base URLs.
    * Resets only its own data (its own backend process / in-memory DB).
    * Tears down ONLY the process tree it created (tracked PIDs), then polls
      until the harness-owned ports are free and the tracked PIDs have exited.
    * Never a fixed sleep as readiness: it polls listeners/PIDs, then requires
      several consecutive health reads before declaring readiness.
    * State-changing commands exit non-zero on real failure; every declared
      state (UP / port free) has a harness-owned read-back poll.

  This script only ever touches processes it recorded under
  artifacts/e2e/lifecycle/<id>.json -- it never kills by port or image name.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory, Position = 0)]
  [ValidateSet('up', 'down', 'reset', 'status')]
  [string]$Command,

  [string]$LifecycleId,
  [int]$BackendPort = 8080,
  [int]$FrontendPort = 3000,
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

# Repo root is the parent of e2e/ (scripts/ -> e2e/ -> repo root).
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
$StateDir = Join-Path $RepoRoot 'artifacts/e2e/lifecycle'
$JarPath = Join-Path $RepoRoot 'backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar'

function Get-StateFile([string]$id) {
  if ([string]::IsNullOrWhiteSpace($id)) {
    throw 'LifecycleId is required.'
  }
  New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
  Join-Path $StateDir "$id.json"
}

function Test-PortFree([int]$port) {
  # Read-back check: true when no listener holds the port.
  $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  return ($null -eq $conns)
}

function Wait-PortFree([int]$port, [int]$timeoutSec = 60) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    if (Test-PortFree $port) { return $true }
    Start-Sleep -Milliseconds 250
  }
  return $false
}

function Test-HealthUp([int]$port) {
  try {
    $resp = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" `
      -TimeoutSec 5 -ErrorAction Stop
    return ($resp.status -eq 'UP')
  } catch {
    return $false
  }
}

function Wait-HealthReady([int]$port, [int]$timeoutSec = 180, [int]$consecutive = 3) {
  # Never a fixed sleep as readiness: poll until several consecutive UP reads.
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  $hits = 0
  while ((Get-Date) -lt $deadline) {
    if (Test-HealthUp $port) {
      $hits++
      if ($hits -ge $consecutive) { return $true }
    } else {
      $hits = 0
    }
    Start-Sleep -Milliseconds 500
  }
  return $false
}

function Invoke-Build {
  Write-Host '==> Building backend (mvn package, tests skipped)'
  Push-Location (Join-Path $RepoRoot 'backend')
  try { mvn -q -DskipTests package } finally { Pop-Location }

  Write-Host '==> Building frontend (npm ci)'
  Push-Location (Join-Path $RepoRoot 'frontend')
  try { npm ci --no-fund --no-audit } finally { Pop-Location }
}

function Start-Lifecycle {
  $stateFile = Get-StateFile $LifecycleId
  if (Test-Path $stateFile) {
    throw "Lifecycle '$LifecycleId' already has a state file: $stateFile. Run 'down' first."
  }
  if (-not (Test-PortFree $BackendPort)) {
    throw "Backend port $BackendPort is already in use. Choose a free -BackendPort."
  }
  if (-not (Test-PortFree $FrontendPort)) {
    throw "Frontend port $FrontendPort is already in use. Choose a free -FrontendPort."
  }

  if (-not $SkipBuild) { Invoke-Build }
  if (-not (Test-Path $JarPath)) {
    throw "Backend jar missing: $JarPath. Run 'up' without -SkipBuild first."
  }

  Write-Host "==> [$LifecycleId] starting backend on :$BackendPort (dev profile)"
  # The SPA runs on an isolated frontend port; the dev CORS default only allows
  # :3000, so pass the actual origin as the allow-list for this lifecycle.
  $backend = Start-Process -PassThru -NoNewWindow java `
    -ArgumentList '-jar', $JarPath, `
      '--spring.profiles.active=dev', `
      "--server.port=$BackendPort", `
      "--app.cors.allowed-origins=http://localhost:$FrontendPort"

  Write-Host "==> [$LifecycleId] starting frontend on :$FrontendPort (vite dev server)"
  # npm is a .cmd shim -- go through cmd.exe so the tree is killable. The SPA's
  # API base defaults to :8080; point it at this lifecycle's backend port via
  # the inherited environment (child cmd.exe inherits this process env).
  $env:VITE_API_BASE = "http://localhost:$BackendPort"
  $frontend = Start-Process -PassThru -NoNewWindow cmd.exe `
    -ArgumentList '/c', 'npm', 'run', 'dev', '--', '--port', "$FrontendPort", '--strictPort' `
    -WorkingDirectory (Join-Path $RepoRoot 'frontend')

  $state = [ordered]@{
    lifecycle_id  = $LifecycleId
    backend_port  = $BackendPort
    frontend_port = $FrontendPort
    backend_pid   = $backend.Id
    frontend_pid  = $frontend.Id
    base_url      = "http://localhost:$FrontendPort"
    api_url       = "http://localhost:$BackendPort"
    started_at    = (Get-Date).ToUniversalTime().ToString('o')
  }
  $state | ConvertTo-Json | Set-Content -Path $stateFile -Encoding utf8

  Write-Host "==> [$LifecycleId] waiting for backend health (consecutive UP reads)"
  if (-not (Wait-HealthReady $BackendPort)) {
    Write-Warning "[$LifecycleId] backend did not become healthy. Tearing down."
    Stop-Lifecycle
    throw "Lifecycle '$LifecycleId' failed to reach a healthy state."
  }

  Write-Host "==> [$LifecycleId] READY"
  Write-Host "    lifecycle id : $LifecycleId"
  Write-Host "    base url     : $($state.base_url)"
  Write-Host "    api url      : $($state.api_url)"
  Write-Host "    backend pid  : $($state.backend_pid)"
  Write-Host "    frontend pid : $($state.frontend_pid)"
}

function Stop-Lifecycle {
  $stateFile = Get-StateFile $LifecycleId
  if (-not (Test-Path $stateFile)) {
    Write-Warning "No state file for lifecycle '$LifecycleId'. Nothing to stop."
    return
  }
  $state = Get-Content $stateFile -Raw | ConvertFrom-Json
  $trackedPids = @($state.backend_pid, $state.frontend_pid)

  foreach ($trackedPid in $trackedPids) {
    if ($null -eq $trackedPid) { continue }
    $proc = Get-Process -Id $trackedPid -ErrorAction SilentlyContinue
    if ($null -ne $proc) {
      Write-Host "==> [$LifecycleId] killing tracked process tree pid $trackedPid"
      # /T kills the whole tree so cmd.exe -> npm -> node doesn't orphan vite.
      # taskkill printing "process not found" is a benign diagnostic; a real
      # failure to kill a live tree surfaces as the poll below timing out.
      taskkill /T /F /PID $trackedPid 2>&1 | Out-Null
    }
  }

  $portsFree = (Wait-PortFree $state.backend_port) -and (Wait-PortFree $state.frontend_port)
  $pidsGone = $true
  foreach ($trackedPid in $trackedPids) {
    if ($null -ne $trackedPid -and $null -ne (Get-Process -Id $trackedPid -ErrorAction SilentlyContinue)) {
      $pidsGone = $false
    }
  }

  if (-not ($portsFree -and $pidsGone)) {
    throw "Lifecycle '$LifecycleId' teardown incomplete. Ports or tracked PIDs still alive."
  }

  Remove-Item $stateFile -Force
  Write-Host "==> [$LifecycleId] stopped; ports released and tracked PIDs exited."
}

function Show-Status {
  $stateFile = Get-StateFile $LifecycleId
  if (-not (Test-Path $stateFile)) {
    Write-Host "Lifecycle '$LifecycleId': not running (no state file)."
    return
  }
  $state = Get-Content $stateFile -Raw | ConvertFrom-Json
  $healthy = Test-HealthUp $state.backend_port
  Write-Host "Lifecycle '$LifecycleId': backend :$($state.backend_port) healthy=$healthy, base=$($state.base_url)"
}

switch ($Command) {
  'up'     { Start-Lifecycle }
  'down'   { Stop-Lifecycle }
  'reset'  { Stop-Lifecycle; Start-Lifecycle }
  'status' { Show-Status }
}
