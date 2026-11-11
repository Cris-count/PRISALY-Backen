# Single-command local start of the PRISALY API.
# - Never creates users or demo data.
# - Never enables pending academic policies (DD-02 student access, M7 SMTP worker stay off).
# - Tests are NOT executed here; run them separately (see message printed after the build).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Fail([string]$message) {
    Write-Host ""
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

function Step([string]$message) {
    Write-Host "==> $message" -ForegroundColor Cyan
}

# Fills only variables that are not already defined in the environment.
function Import-EnvFile([string]$path) {
    $loaded = @()
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) { continue }
        $name = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim()
        if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
            $loaded += $name
        }
    }
    return $loaded
}

function Require-Env([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if (-not $value) { Fail "Falta la variable de entorno $name (defínela o añádela a .env; ver .env.example)." }
    return $value
}

# Sends the PostgreSQL SSLRequest packet; a real server answers 'S' or 'N'.
# Detects ports taken by other processes (e.g. a netsh portproxy or wslrelay on localhost:5432).
function Test-PostgresProtocol([string]$hostName, [int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.ReceiveTimeout = 3000
        $client.SendTimeout = 3000
        $client.Connect($hostName, $port)
        $stream = $client.GetStream()
        $sslRequest = [byte[]](0, 0, 0, 8, 4, 210, 22, 47)
        $stream.Write($sslRequest, 0, $sslRequest.Length)
        $answer = $stream.ReadByte()
        return ($answer -eq [byte][char]'S') -or ($answer -eq [byte][char]'N')
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-Tcp([string]$hostName, [int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($hostName, $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(2000)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# 1. Java >= 21 (the build targets release 21).
Step "Comprobando Java"
$javaExe = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
if (-not (Get-Command $javaExe -ErrorAction SilentlyContinue)) {
    Fail "No se encontró Java. Instala un JDK 21 y define JAVA_HOME o añade java al PATH."
}
$versionLine = (& cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1)
if ($versionLine -notmatch 'version "(\d+)') { Fail "No se pudo leer la versión de Java: $versionLine" }
$javaMajor = [int]$Matches[1]
if ($javaMajor -lt 21) { Fail "Se requiere Java 21; encontrado: $versionLine" }
if ($javaMajor -ne 21) {
    Write-Host "AVISO: el proyecto apunta a Java 21 y se usará Java $javaMajor ($versionLine). Compila con release 21." -ForegroundColor Yellow
} else {
    Write-Host "Java: $versionLine"
}

# 2. Maven Wrapper.
Step "Comprobando Maven Wrapper"
if (-not (Test-Path ".\mvnw.cmd") -or -not (Test-Path ".\.mvn\wrapper\maven-wrapper.properties")) {
    Fail "No se encontró mvnw.cmd o .mvn\wrapper\maven-wrapper.properties en $PSScriptRoot."
}

# 3. Configuration from the environment (.env fills only missing variables).
Step "Leyendo configuración"
if (Test-Path ".env") {
    $loaded = Import-EnvFile ".env"
    Write-Host "Variables tomadas de .env (no definidas antes): $($loaded -join ', ')"
} else {
    Write-Host "No hay .env; se usan solo variables de entorno."
}
$databaseUrl = Require-Env "DATABASE_URL"
[void](Require-Env "DATABASE_USER")
[void](Require-Env "DATABASE_PASSWORD")
$appPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
$env:APP_PORT = $appPort

# The frontend (PRISALY-Front, Angular dev server) runs on http://localhost:4200 and calls the API directly.
$frontOrigin = if ($env:PRISALY_FRONT_ORIGIN) { $env:PRISALY_FRONT_ORIGIN } else { "http://localhost:4200" }
$cors = if ($env:CORS_ORIGINS) { $env:CORS_ORIGINS } else { "http://localhost:4200" }
if (($cors -split ",") -notcontains $frontOrigin) {
    $cors = "$cors,$frontOrigin"
    Write-Host "CORS_ORIGINS ampliado para esta ejecución con $frontOrigin" -ForegroundColor Yellow
}
$env:CORS_ORIGINS = $cors
Write-Host "DATABASE_URL=$databaseUrl  DATABASE_USER=$env:DATABASE_USER  APP_PORT=$appPort  CORS_ORIGINS=$cors"

# 4. PostgreSQL reachable.
Step "Comprobando PostgreSQL"
if ($databaseUrl -notmatch '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/') {
    Fail "DATABASE_URL no tiene el formato jdbc:postgresql://host:puerto/base"
}
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { [int]$Matches[2] } else { 5432 }
# Docker Desktop's WSL relay resets the PostgreSQL SSL negotiation on localhost; .env.example already uses sslmode=disable.
if ((@("localhost", "127.0.0.1") -contains $dbHost) -and ($databaseUrl -notmatch 'sslmode=')) {
    $separator = if ($databaseUrl.Contains("?")) { "&" } else { "?" }
    $databaseUrl = "$databaseUrl${separator}sslmode=disable"
    $env:DATABASE_URL = $databaseUrl
    Write-Host "AVISO: DATABASE_URL local sin sslmode; se usa sslmode=disable en esta ejecución: $databaseUrl" -ForegroundColor Yellow
}
if (-not (Test-Tcp $dbHost $dbPort)) {
    $isLocal = @("localhost", "127.0.0.1") -contains $dbHost
    if ($isLocal -and (Test-Path ".\compose.yaml") -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        docker info *> $null
        if ($LASTEXITCODE -ne 0) {
            Fail "PostgreSQL no responde en ${dbHost}:${dbPort} y Docker Desktop no está en ejecución. Ábrelo o arranca tu PostgreSQL."
        }
        Write-Host "PostgreSQL no responde en ${dbHost}:${dbPort}; arrancando el servicio postgres de compose.yaml (sin datos de prueba)."
        docker compose up -d postgres
        if ($LASTEXITCODE -ne 0) { Fail "docker compose no pudo arrancar PostgreSQL." }
        $ready = $false
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 2
            if (Test-Tcp $dbHost $dbPort) { $ready = $true; break }
        }
        if (-not $ready) { Fail "PostgreSQL no quedó accesible en ${dbHost}:${dbPort}." }
    } else {
        Fail "PostgreSQL no es accesible en ${dbHost}:${dbPort}. Arráncalo o corrige DATABASE_URL."
    }
}
if (-not (Test-PostgresProtocol $dbHost $dbPort)) {
    $listeners = Get-NetTCPConnection -LocalPort $dbPort -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { "$($_.LocalAddress) -> $((Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName)" }
    Fail ("${dbHost}:${dbPort} acepta TCP pero no responde como PostgreSQL. Otro proceso ocupa ese puerto: " +
        ($listeners -join "; ") +
        ". Libera el puerto, cambia POSTGRES_PORT (y DATABASE_URL) o apunta DATABASE_URL a una dirección que llegue a tu PostgreSQL.")
}
Write-Host "PostgreSQL accesible en ${dbHost}:${dbPort} (protocolo verificado)"

# 5. API port free.
if (Test-Tcp "localhost" ([int]$appPort)) {
    Fail "El puerto $appPort ya está en uso. Detén el proceso que lo ocupa o define APP_PORT."
}

# 6. Build app and the modules it needs. Test sources are compiled; tests are not run here.
Step "Compilando app y módulos (mvnw -pl app -am package)"
& .\mvnw.cmd -B -pl app -am package -DskipTests
if ($LASTEXITCODE -ne 0) { Fail "La compilación falló (ver salida de Maven arriba)." }
Write-Host "PRUEBAS NO EJECUTADAS en el arranque. Para ejecutarlas: .\mvnw.cmd -pl app -am verify" -ForegroundColor Yellow

$jar = Get-ChildItem ".\app\target" -Filter "app-*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) { Fail "No se encontró el JAR de app en app\target." }

# 7. Run in the foreground; pending policies are forced off regardless of the environment.
Step "Iniciando API: http://localhost:$appPort/api/health  (Ctrl+C para detener)"
& $javaExe -jar $jar.FullName `
    "--prisaly.m1.student-access.enabled=false" `
    "--prisaly.m7.worker.enabled=false"
exit $LASTEXITCODE
