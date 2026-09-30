Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repoRoot

git config --local core.hooksPath .githooks
Write-Host 'Git hooks configured: core.hooksPath=.githooks'
