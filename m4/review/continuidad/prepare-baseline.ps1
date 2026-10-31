param([string]$BaselineRef = 'a42c738027f86915a1284905df24e6e4c28422b4')
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $repositoryRoot
try {
    $resolvedRef = (git rev-parse --verify "${BaselineRef}^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $resolvedRef -notmatch '^[a-f0-9]{40}$') {
        throw 'Referencia de línea base no válida'
    }
    $destination = Join-Path $repositoryRoot 'm4/target/bd0052-baseline'
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $files = @(git ls-tree -r --name-only $resolvedRef app/src/main/resources/db/migration |
        Where-Object { $_ -match '/V00[1-9]__[^/]+\.sql$' })
    if ($files.Count -ne 9) { throw 'Se requieren exactamente V001-V009' }
    $manifest = @("Baseline commit: $resolvedRef")
    foreach ($file in $files) {
        $sql = git show "${resolvedRef}:$file"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo leer $file" }
        $output = Join-Path $destination ([IO.Path]::GetFileName($file))
        [IO.File]::WriteAllText($output, ($sql -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
        $expectedBlob = (git rev-parse "${resolvedRef}:$file").Trim()
        $actualBlob = (git hash-object --no-filters -- $output).Trim()
        if ($actualBlob -ne $expectedBlob) { throw "Snapshot no idéntico a Git: $file" }
        $manifest += "$expectedBlob  $file"
    }
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'baseline-manifest.txt'), $manifest,
        [Text.UTF8Encoding]::new($false))
    Write-Output "Snapshot verificado en $destination. No se modifica ninguna migración de producción."
} finally {
    Pop-Location
}
