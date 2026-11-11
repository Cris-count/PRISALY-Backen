# Arranca PostgreSQL y la API de PRISALY. Ctrl+C detiene la API; la base sigue en Docker.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Read-EnvFile([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) { continue }
        $values[$trimmed.Substring(0, $eq).Trim()] = $trimmed.Substring($eq + 1).Trim()
    }
    return $values
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no está instalado o no está en PATH."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    $desktop = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $desktop)) {
        throw "Docker Desktop no está en ejecución. Ábrelo y vuelve a lanzar .\iniciar.cmd"
    }
    Write-Host "Iniciando Docker Desktop..."
    Start-Process $desktop | Out-Null
    $listo = $false
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { $listo = $true; break }
    }
    if (-not $listo) { throw "Docker Desktop no respondió a tiempo." }
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Se creó .env a partir de .env.example. Cámbialo si quieres otra contraseña local."
}

$envFile = Read-EnvFile ".env"
$db = $envFile["POSTGRES_DB"]
$user = $envFile["POSTGRES_USER"]
$password = $envFile["POSTGRES_PASSWORD"]
$appPort = if ($envFile["APP_PORT"]) { $envFile["APP_PORT"] } else { "8080" }
$cors = if ($envFile["CORS_ORIGINS"]) { $envFile["CORS_ORIGINS"] } else { "http://localhost:4200" }
if (-not $db -or -not $user -or -not $password) {
    throw ".env debe definir POSTGRES_DB, POSTGRES_USER y POSTGRES_PASSWORD."
}

Write-Host "Levantando PostgreSQL..."
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "docker compose no pudo levantar PostgreSQL." }

$sano = $false
for ($i = 0; $i -lt 30; $i++) {
    docker compose exec -T postgres pg_isready -U $user -d $db *> $null
    if ($LASTEXITCODE -eq 0) { $sano = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $sano) { throw "PostgreSQL no quedó listo." }

Write-Host "Compilando la API..."
& .\mvnw.cmd -B -pl app -am package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "La compilación falló." }

$network = docker inspect prisaly-postgres --format "{{range `$k, `$v := .NetworkSettings.Networks}}{{`$k}}{{end}}"
if ($LASTEXITCODE -ne 0 -or -not $network) { throw "No se encontró la red de prisaly-postgres." }

docker rm -f prisaly-app *> $null
$runtimeEnv = Join-Path $env:TEMP "prisaly-app-runtime.env"
@(
    "DATABASE_URL=jdbc:postgresql://postgres:5432/${db}?sslmode=disable"
    "DATABASE_USER=$user"
    "DATABASE_PASSWORD=$password"
    "APP_PORT=8080"
    "CORS_ORIGINS=$cors"
) | Set-Content -Path $runtimeEnv -Encoding ascii

Write-Host "API en http://localhost:${appPort}/api/health"
try {
    docker run --rm --name prisaly-app --network $network -p "${appPort}:8080" `
        --env-file $runtimeEnv `
        -v "${PWD}/app/target:/app:ro" `
        -w /app eclipse-temurin:21-jre `
        java -jar app-0.0.1-SNAPSHOT.jar
} finally {
    Remove-Item $runtimeEnv -ErrorAction SilentlyContinue
}
