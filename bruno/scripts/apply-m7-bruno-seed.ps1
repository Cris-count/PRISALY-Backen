# Applies M1 then M7 local Bruno seeds to Compose Postgres. Never commit secrets.
$ErrorActionPreference = "Stop"
$backendRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $backendRoot

$m1Seed = Join-Path $PSScriptRoot "apply-m1-bruno-seed.ps1"
$m7Sql = Join-Path $PSScriptRoot "m7-bruno-seed-local.sql"
if (-not (Test-Path $m1Seed)) {
  throw "M1 seed script not found: $m1Seed"
}
if (-not (Test-Path $m7Sql)) {
  throw "M7 seed SQL not found: $m7Sql"
}
if (-not (Test-Path (Join-Path $backendRoot "compose.yaml"))) {
  throw "compose.yaml not found under $backendRoot"
}

& $m1Seed
if ($LASTEXITCODE -ne 0) {
  throw "M1 seed failed (exit $LASTEXITCODE)"
}

Get-Content $m7Sql -Raw | docker compose exec -T postgres psql -U prisaly -d prisaly -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
  throw "Failed to apply M7 seed (docker exit $LASTEXITCODE)."
}
Write-Output "M7 Bruno seed applied (GESTIONAR_SESION, estudiante, caso Publicado)."
