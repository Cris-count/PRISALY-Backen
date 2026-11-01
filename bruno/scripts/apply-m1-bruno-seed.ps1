# Applies local-only Bruno seed to Compose Postgres. Never commit secrets.
$ErrorActionPreference = "Stop"
$backendRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $backendRoot
$sql = Join-Path $PSScriptRoot "m1-bruno-seed-local.sql"
if (-not (Test-Path $sql)) {
  throw "Seed SQL not found: $sql"
}
if (-not (Test-Path (Join-Path $backendRoot "compose.yaml"))) {
  throw "compose.yaml not found under $backendRoot"
}
Get-Content $sql -Raw | docker compose exec -T postgres psql -U prisaly -d prisaly -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
  throw "Failed to apply seed (docker exit $LASTEXITCODE). Is Docker Desktop running and postgres healthy?"
}
Write-Output "Seed applied to local Compose database prisaly."

$secretsExample = Join-Path $backendRoot "bruno\environments\local.secrets.bru.example"
$secrets = Join-Path $backendRoot "bruno\environments\local.secrets.bru"
if (-not (Test-Path $secrets)) {
  Copy-Item $secretsExample $secrets
  Write-Output "Created gitignored local.secrets.bru from example. Select environment 'local.secrets' in Bruno."
}
