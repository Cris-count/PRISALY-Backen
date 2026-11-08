param(
    [string]$ExpectedSha = 'd8eb62d0e4873ea28f426ed3d59392a5cd1ae95c',
    [string]$MockitoAgent = ''
)
$ErrorActionPreference = 'Stop'
# DC-MACRO-04 autoriza limpieza solo dentro de PostgreSQLContainer desechables.
# No modifica el checkout, no usa compose y no recibe conexiones externas.
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repositoryRoot
$previousReuse = $env:TESTCONTAINERS_REUSE_ENABLE
try {
    $actualSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $actualSha -ne $ExpectedSha) {
        throw 'El checkout no corresponde al SHA acordado; conciliar antes de ejecutar.'
    }
    $functionalChanges = @(git diff HEAD --name-only -- '*/src/main' '*/src/test' '*/pom.xml' 'pom.xml')
    if ($LASTEXITCODE -ne 0 -or $functionalChanges.Count -gt 0) {
        throw 'Existen cambios funcionales sin commit; identificar el candidato antes de ejecutar.'
    }
    docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine no disponible; no se inicia Maven.' }
    $baseline = (Resolve-Path 'app/src/main/resources/db/migration').Path.Replace('\', '/')
    $migrationNames = @(Get-ChildItem -LiteralPath $baseline -File | Select-Object -ExpandProperty Name)
    if ($migrationNames.Count -ne 9 -or @($migrationNames | Where-Object { $_ -notmatch '^V00[1-9]__.+\.sql$' }).Count -gt 0) {
        throw 'La línea de migraciones debe ser exclusivamente V001–V009.'
    }
    $env:TESTCONTAINERS_REUSE_ENABLE = 'false'
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $outputDirectory = Join-Path $repositoryRoot "m4/target/DC-MACRO-04-$stamp"
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $started = Get-Date
    $selected = @(
        'Bd0052BaselineIT#rf09RealDeBaseDatosRechazaBorrador+publicadoProgramaYBloqueaContenidoConTriggerReal+rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion',
        'M4M1ContratosPostgreSqlIT', 'ProgramacionParticipacionPostgreSqlIT',
        'SesionAcademicaPostgreSqlIT', 'ParticipacionesM4DesdeM5IT', 'InicioIntentoIT',
        'HistorialM4M5IT', 'ParticipacionesPortWiringIT', 'NotificacionPortWiringIT',
        'ConsultaIntentoEvaluacionM5M6IT'
    )
    # El diagnóstico que aplica V010 queda excluido por selección de métodos.
    $mavenArguments = @(
        '-B', '-pl', 'app', '-am', 'verify', '-Pdocker-it',
        '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest,M4CoordinacionInicioTest,M4ProviderSecurityTest,ParticipacionesM4DesdeM5Test',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        "-Dit.test=$($selected -join ',')", '-Dfailsafe.failIfNoSpecifiedTests=false',
        "-Dm4.baseline.migrations=$baseline"
    )
    if ($MockitoAgent) {
        $agent = (Resolve-Path -LiteralPath $MockitoAgent).Path
        if ($agent -match '\s') { throw 'Usar una ruta de agente sin espacios.' }
        $mavenArguments += "-DargLine=-javaagent:$agent"
    }
    & .\mvnw.cmd @mavenArguments 2>&1 | Tee-Object -FilePath (Join-Path $outputDirectory 'maven.log')
    $mavenExit = $LASTEXITCODE
    $reports = @()
    foreach ($module in @('m1','m2','m3','m4','m5','m6','m7','app')) {
        foreach ($phase in @('surefire','failsafe')) {
            $reportDirectory = Join-Path $repositoryRoot "$module/target/$phase-reports"
            if (-not (Test-Path -LiteralPath $reportDirectory)) { continue }
            Get-ChildItem -LiteralPath $reportDirectory -File |
                Where-Object { $_.Name -like 'TEST-*.xml' -and $_.LastWriteTime -ge $started } |
                ForEach-Object {
                    [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw
                    $reports += [pscustomobject]@{
                        module=$module; phase=$phase; suite=$xml.testsuite.name
                        tests=[int]$xml.testsuite.tests; failures=[int]$xml.testsuite.failures
                        errors=[int]$xml.testsuite.errors; skipped=[int]$xml.testsuite.skipped
                    }
                }
        }
    }
    [ordered]@{
        sha=$actualSha; started=$started.ToString('o'); exitCode=$mavenExit
        migrations=$migrationNames; suites=$reports
        note='Resumen sin propiedades JVM. Revisar secretos antes de compartir maven.log o XML originales.'
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $outputDirectory 'resumen.json') -Encoding utf8
    $runtime = @($reports | Where-Object { $_.phase -eq 'failsafe' })
    $missing = @($selected | ForEach-Object { ($_ -split '#')[0] } | Where-Object {
        $required = $_
        @($runtime | Where-Object { $_.suite -like "*.$required" -and $_.tests -gt 0 }).Count -eq 0
    })
    if ($mavenExit -ne 0 -or $missing.Count -gt 0 -or
        @($runtime | Where-Object { $_.skipped -gt 0 -or $_.errors -gt 0 -or $_.failures -gt 0 }).Count -gt 0) {
        throw "Campaña incompleta/fallida. Suites ausentes: $($missing -join ', '). Evidencia: $outputDirectory"
    }
    Write-Output "Campaña finalizada. Revisar resultados y límites de fixtures: $outputDirectory"
}
finally {
    $env:TESTCONTAINERS_REUSE_ENABLE = $previousReuse
    Pop-Location
}
