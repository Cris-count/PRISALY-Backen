<#
PRISALY · integracion/alineacion-final · preparación y arranque del candidato coordinado (Backend + Frontend).

Uso típico (desde cualquier carpeta):
  powershell -ExecutionPolicy Bypass -File ACTUALIZAR-PRISALY.ps1 -Backend D:\src\PRISALY-Backen -Front D:\src\PRISALY-Front
  ... -SoloPreparar      # descarga, verifica SHA, crea worktrees y construye imágenes; no arranca
  ... -Detener           # detiene el proyecto (sin borrar volúmenes)

Garantías:
  - No cambia ni reinicia las ramas de trabajo: usa worktrees separados en modo detached sobre los SHA del manifiesto.
  - Se detiene si hay cambios locales sin confirmar en los repositorios indicados.
  - No borra volúmenes ni datos. Usa un proyecto Docker propio (por defecto «prisaly-alineacion») con volúmenes propios.
  - Si el volumen de PostgreSQL ya existe, comprueba su historial Flyway contra el manifiesto ANTES de arrancar el
    Backend; si no coincide exactamente, se detiene. Nunca ejecuta repair, clean ni migraciones fuera de orden.
  - Los secretos se generan fuera del repositorio y nunca se imprimen.
#>
param(
  [string]$Backend,
  [string]$Front,
  [string]$Rama = 'integracion/alineacion-final',
  [string]$Proyecto = 'prisaly-alineacion',
  [string]$Ejecucion = (Join-Path $env:LOCALAPPDATA 'prisaly-alineacion'),
  [switch]$SoloPreparar,
  [switch]$Detener,
  [switch]$Reconstruir
)
$ErrorActionPreference = 'Stop'
function Paso($t) { Write-Host "`n== $t" -ForegroundColor Cyan }
function Alto($t) { Write-Host "DETENIDO: $t" -ForegroundColor Red; exit 1 }
function Git($repo) { $a = $args; $o = & git -C $repo @a 2>&1; if ($LASTEXITCODE -ne 0) { Alto "git $($a -join ' ') en $repo :: $o" }; $o }

# 1. Rutas
Paso 'Repositorios'
foreach ($cand in @((Get-Location).Path, (Split-Path $PSScriptRoot -Parent | Split-Path -Parent | Split-Path -Parent))) {
  if (-not $Backend -and (Test-Path (Join-Path $cand 'PRISALY-Backen\.git'))) { $Backend = Join-Path $cand 'PRISALY-Backen' }
  if (-not $Front -and (Test-Path (Join-Path $cand 'PRISALY-Front\.git'))) { $Front = Join-Path $cand 'PRISALY-Front' }
}
if (-not $Backend -or -not (Test-Path (Join-Path $Backend '.git'))) { Alto 'Indica -Backend <ruta del repositorio PRISALY-Backen>.' }
if (-not $Front -or -not (Test-Path (Join-Path $Front '.git'))) { Alto 'Indica -Front <ruta del repositorio PRISALY-Front>.' }
"Backend: $Backend"; "Frontend: $Front"; "Ejecución: $Ejecucion"
$compose = $null

if ($Detener) {
  $cfg = Join-Path $Ejecucion 'compose.yaml'
  if (-not (Test-Path $cfg)) { Alto "No hay ejecución preparada en $Ejecucion" }
  docker compose -p $Proyecto --project-directory $Ejecucion -f $cfg --env-file (Join-Path $Ejecucion '.env') stop
  'Servicios detenidos. Volúmenes y datos conservados.'; exit 0
}

# 2-3. Estado Git: no continuar con cambios que puedan perderse
Paso 'Estado Git'
foreach ($r in $Backend, $Front) {
  $sucio = Git $r status --porcelain --untracked-files=no
  if ($sucio) { Alto "Hay cambios sin confirmar en $r. Confírmalos o guárdalos (git stash) y vuelve a ejecutar." }
  "$r : limpio (rama actual $(Git $r branch --show-current)) — no se modificará."
}

# 4. Descargar referencias publicadas
Paso "Descargando $Rama"
foreach ($r in $Backend, $Front) { Git $r fetch origin "+refs/heads/${Rama}:refs/remotes/origin/$Rama" | Out-Null }

# 5. Manifiesto canónico (Backend) y verificación de SHA
Paso 'Manifiesto'
$texto = Git $Backend show "origin/${Rama}:ops/alineacion-final/PRISALY-INTEGRACION-MANIFEST.json"
$m = ($texto -join "`n") | ConvertFrom-Json
$shaBack = $m.backend.shaCodigo; $shaFront = $m.frontend.shaCodigo
foreach ($p in @(@($Backend, $shaBack, 'Backend'), @($Front, $shaFront, 'Frontend'))) {
  & git -C $p[0] cat-file -e "$($p[1])^{commit}" 2>$null
  if ($LASTEXITCODE -ne 0) { Alto "$($p[2]): el SHA $($p[1]) del manifiesto no existe tras el fetch." }
  & git -C $p[0] merge-base --is-ancestor $p[1] "origin/$Rama"
  if ($LASTEXITCODE -ne 0) { Alto "$($p[2]): $($p[1]) no pertenece a origin/$Rama. El manifiesto y la rama no coinciden." }
  "$($p[2]): $($p[1]) verificado en origin/$Rama"
}

# 6. Worktrees independientes (detached) — no tocan las ramas de trabajo
Paso 'Worktrees'
$wtB = Join-Path $Ejecucion 'backend'; $wtF = Join-Path $Ejecucion 'frontend'; $ops = Join-Path $Ejecucion 'ops'
New-Item -ItemType Directory -Force $Ejecucion | Out-Null
foreach ($w in @(@($Backend, $wtB, $shaBack), @($Front, $wtF, $shaFront))) {
  if (Test-Path $w[1]) {
    $sucio = & git -C $w[1] status --porcelain --untracked-files=no
    if ($sucio) { Alto "El worktree $($w[1]) tiene cambios; no se sobrescribe." }
    Git $w[1] checkout --detach $w[2] | Out-Null
  } else { Git $w[0] worktree add --detach $w[1] $w[2] | Out-Null }
  "$($w[1]) -> $(Git $w[1] rev-parse --short HEAD)"
}
# Configuración de ejecución tomada de la rama publicada (no del worktree de código).
if (Test-Path $ops) { Remove-Item $ops -Recurse -Force }
$tar = Join-Path $Ejecucion 'ops.tar'
& git -C $Backend archive --format=tar -o $tar "origin/$Rama" ops/alineacion-final
if ($LASTEXITCODE -ne 0) { Alto 'git archive de ops/alineacion-final falló.' }
$tmp = Join-Path $Ejecucion 'ops-extraido'; if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Force $tmp | Out-Null
& tar -xf $tar -C $tmp; if ($LASTEXITCODE -ne 0) { Alto 'No se pudo extraer ops/alineacion-final.' }
Move-Item (Join-Path $tmp 'ops\alineacion-final') $ops
Remove-Item $tmp, $tar -Recurse -Force
Copy-Item (Join-Path $ops 'compose.yaml') (Join-Path $Ejecucion 'compose.yaml') -Force
Copy-Item (Join-Path $ops 'compose.bootstrap.yaml') (Join-Path $Ejecucion 'compose.bootstrap.yaml') -Force

# 7. Dependencias
Paso 'Dependencias'
foreach ($c in 'docker', 'java', 'javac', 'node', 'npm') { if (-not (Get-Command $c -ErrorAction SilentlyContinue)) { Alto "Falta $c en el PATH." } }
docker compose version | Out-Null; if ($LASTEXITCODE -ne 0) { Alto 'Docker Compose no disponible (¿Docker Desktop arrancado?).' }
$java = (& java -version 2>&1 | Select-Object -First 1).ToString()
if ($java -notmatch '"(2[1-9]|[3-9]\d)') { Alto "Se requiere JDK 21 o superior (detectado: $java)." }
"Docker, JDK ($java), Node $(node --version) — OK"

# Secretos locales (fuera del repositorio; nunca se imprimen)
$sec = Join-Path $Ejecucion 'secretos'; New-Item -ItemType Directory -Force $sec | Out-Null
function Aleatorio([int]$n) { $b = New-Object byte[] $n; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) }
function Secreto($nombre, $valor) { $p = Join-Path $sec $nombre; if (-not (Test-Path $p)) { [IO.File]::WriteAllText($p, $valor) } }
Secreto 'postgres-password' (Aleatorio 32)
Secreto 'codigo.key' ('qa1:' + (Aleatorio 32))
Secreto 'sesion.key' ('qa1:' + (Aleatorio 32))
Secreto 'outbox.key' ('qa1:' + (Aleatorio 32))
Secreto 'admin-bootstrap' (Aleatorio 24)
if (-not (Test-Path (Join-Path $sec 'qa-admin.txt'))) { Copy-Item (Join-Path $sec 'admin-bootstrap') (Join-Path $sec 'qa-admin.txt') }
Secreto 'qa-docente.txt' ''
$envFile = Join-Path $Ejecucion '.env'
@("WEB_PORT=18175", "MAILPIT_PORT=18030", "SMTP_PORT=11030", "PRISALY_BACKEND_SHA=$shaBack", "PRISALY_FRONTEND_SHA=$shaFront") | Set-Content -Encoding ascii $envFile
"Secretos en $sec (generados solo si faltaban)."

# 8-9. Imágenes: reutilizar si ya existen para el mismo SHA
Paso 'Imágenes'
$imgB = "prisaly-alineacion-backend:$shaBack"; $imgF = "prisaly-alineacion-frontend:$shaFront"
$ctx = Join-Path $ops '.build'
docker image inspect $imgB *> $null
if ($LASTEXITCODE -ne 0 -or $Reconstruir) {
  Push-Location $wtB
  try { & .\mvnw.cmd -q -DskipTests package; if ($LASTEXITCODE -ne 0) { Alto 'Falló la compilación Maven del Backend.' } } finally { Pop-Location }
  $jar = Get-ChildItem (Join-Path $wtB 'app\target') -Filter 'app-*.jar' | Where-Object { $_.Name -notmatch 'plain|sources' } | Select-Object -First 1
  New-Item -ItemType Directory -Force (Join-Path $ctx 'backend'), (Join-Path $ctx 'health') | Out-Null
  Copy-Item $jar.FullName (Join-Path $ctx 'backend\app.jar') -Force
  & javac -d (Join-Path $ctx 'health') (Join-Path $ops 'Healthcheck.java'); if ($LASTEXITCODE -ne 0) { Alto 'Falló javac Healthcheck.' }
  docker build -q -f (Join-Path $ops 'Dockerfile.backend') -t $imgB --label "org.opencontainers.image.revision=$shaBack" $ops
  if ($LASTEXITCODE -ne 0) { Alto 'Falló la imagen del Backend.' }
} else { "Reutilizada $imgB" }
docker image inspect $imgF *> $null
if ($LASTEXITCODE -ne 0 -or $Reconstruir) {
  Push-Location $wtF
  try {
    if (-not (Test-Path 'node_modules')) { & npm ci --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { Alto 'npm ci falló.' } }
    & npx ng build --configuration $m.frontend.perfilAngular; if ($LASTEXITCODE -ne 0) { Alto 'Falló ng build.' }
  } finally { Pop-Location }
  $dest = Join-Path $ctx 'frontend\browser'; if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
  New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
  Copy-Item (Join-Path $wtF 'dist\PRISALY-Front\browser') $dest -Recurse
  docker build -q -f (Join-Path $ops 'Dockerfile.frontend') -t $imgF --label "org.opencontainers.image.revision=$shaFront" $ops
  if ($LASTEXITCODE -ne 0) { Alto 'Falló la imagen del Frontend.' }
} else { "Reutilizada $imgF" }
if ($SoloPreparar) { "`nPreparado. Ejecuta de nuevo sin -SoloPreparar para arrancar."; exit 0 }

# 10. Compatibilidad del esquema ANTES de arrancar el Backend
Paso 'Esquema'
$dc = @('compose', '-p', $Proyecto, '--project-directory', $Ejecucion, '--env-file', $envFile, '-f', (Join-Path $Ejecucion 'compose.yaml'))
$volumen = "${Proyecto}_pg_data"
docker volume inspect $volumen *> $null
$baseNueva = ($LASTEXITCODE -ne 0)
docker @dc up -d --wait postgres mailpit; if ($LASTEXITCODE -ne 0) { Alto 'PostgreSQL o Mailpit no arrancaron.' }
if (-not $baseNueva) {
  $psql = @('exec', '-T', 'postgres', 'psql', '-U', $m.postgres.usuario, '-d', $m.postgres.baseDatos, '-At', '-c')
  $hay = ((docker @dc @psql "select to_regclass('public.flyway_schema_history') is not null") | Out-String).Trim()
  if ($hay -eq 't') {
    $hist = ((docker @dc @psql "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success") | Out-String).Trim()
    $esperado = ($m.flyway.historialEsperado -join ',')
    if ($hist -ne $esperado) { Alto "Historial Flyway incompatible.`n  Base:      $hist`n  Manifiesto: $esperado`nNo se arranca el Backend. No se ejecuta repair ni clean." }
    "Historial Flyway idéntico al manifiesto ($esperado)."
  } else { $baseNueva = $true }
}
if ($baseNueva) { "Base nueva: Flyway aplicará $($m.flyway.historialEsperado -join ',') y se creará el administrador QA."; $dc += @('-f', (Join-Path $Ejecucion 'compose.bootstrap.yaml')) }

# 11-12. Arranque y healthchecks
Paso 'Arranque'
docker @dc up -d --wait backend frontend
if ($LASTEXITCODE -ne 0) { docker @dc logs --tail 60 backend; Alto 'Backend o Frontend no quedaron saludables (logs arriba).' }
docker @dc ps --format 'table {{.Service}}\t{{.Status}}'

# 13. URL
Paso 'Listo'
"Portal:  http://localhost:18175/acceso"
"Mailpit: http://localhost:18030"
"API:     http://localhost:18175/api/health"
"Detener: powershell -File `"$PSCommandPath`" -Backend `"$Backend`" -Front `"$Front`" -Detener"
if ($baseNueva) { 'Administrador QA: administrador.dc106@prisaly.test (botón «Administrador QA»). Docente y estudiantes: crearlos desde el panel de administración (ver HANDOFF-NICOLAS.md).' }
