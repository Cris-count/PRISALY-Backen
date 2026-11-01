param(
    [Parameter(Mandatory = $true)][datetime]$RunStartedAt,
    [string]$EvidenceDirectory = ''
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$evidence = if ($EvidenceDirectory) { [IO.Path]::GetFullPath($EvidenceDirectory) } else { Join-Path $PSScriptRoot 'evidence' }
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$results = foreach ($module in @('m1', 'm3', 'm4', 'm5', 'app')) {
    foreach ($runner in @('surefire', 'failsafe')) {
        foreach ($file in Get-ChildItem (Join-Path $repositoryRoot "$module/target/$runner-reports/TEST-*.xml") -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $RunStartedAt }) {
            $suite = ([xml](Get-Content -LiteralPath $file.FullName -Raw)).testsuite
            # Solo nombres y resultados: no propiedades, credenciales ni salida de Spring.
            [pscustomobject]@{
                module = $module
                runner = $runner
                suite = [string]$suite.name
                tests = [int]$suite.tests
                failures = [int]$suite.failures
                errors = [int]$suite.errors
                skipped = [int]$suite.skipped
                cases = @($suite.testcase | ForEach-Object { [string]$_.name })
            }
        }
    }
}
$results | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $evidence 'suites.json')
$lines = @('| Módulo | Runner | Pruebas | Fallos | Errores | Omitidas |', '|---|---|---:|---:|---:|---:|')
foreach ($group in $results | Group-Object module, runner) {
    $first = $group.Group[0]
    $sum = @{}
    foreach ($field in @('tests', 'failures', 'errors', 'skipped')) {
        $sum[$field] = ($group.Group | Measure-Object -Property $field -Sum).Sum
    }
    $lines += "| $($first.module) | $($first.runner) | $($sum.tests) | $($sum.failures) | $($sum.errors) | $($sum.skipped) |"
}
$lines | Set-Content -Encoding utf8 (Join-Path $evidence 'conteo.md')
$lines
