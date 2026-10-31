param([string]$EvidenceDirectory = '')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$appPath = Join-Path $repositoryRoot 'app/target/app-0.0.1-SNAPSHOT.jar'
$modulePath = Join-Path $repositoryRoot 'm4/target/m4-0.0.1-SNAPSHOT.jar'
$findings = @()
$appArchive = [IO.Compression.ZipFile]::OpenRead($appPath)
try {
    $directStubs = @($appArchive.Entries | Where-Object {
        $_.FullName -match '^BOOT-INF/classes/.*(StubAdapter|TestM4Application)'
    })
    $directV010 = @($appArchive.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/.*V010' })
    $moduleEntry = $appArchive.GetEntry('BOOT-INF/lib/m4-0.0.1-SNAPSHOT.jar')
    if ($null -eq $moduleEntry) { throw 'Falta M4 en app' }
    $moduleStream = $moduleEntry.Open()
    $moduleBytes = [IO.MemoryStream]::new()
    try { $moduleStream.CopyTo($moduleBytes) } finally { $moduleStream.Dispose() }
    try {
        $sha256 = [Security.Cryptography.SHA256]::Create()
        try {
            $nestedHash = [BitConverter]::ToString($sha256.ComputeHash($moduleBytes.ToArray())).Replace('-', '')
        } finally { $sha256.Dispose() }
        $localHash = (Get-FileHash -LiteralPath $modulePath -Algorithm SHA256).Hash
        if ($nestedHash -ne $localHash) { throw 'app conserva un JAR M4 diferente al compilado actual' }
        $moduleBytes.Position = 0
        $moduleArchive = [IO.Compression.ZipArchive]::new($moduleBytes, [IO.Compression.ZipArchiveMode]::Read, $true)
        try {
            $stubs = @($moduleArchive.Entries | Where-Object { $_.FullName -match 'StubAdapter|TestM4Application' })
            $v010 = @($moduleArchive.Entries | Where-Object { $_.FullName -match 'V010.*\.sql$' })
            $config = $moduleArchive.GetEntry('com/prisaly/m4/infrastructure/config/M4ProviderAutoConfiguration.class')
            $importEntry = $moduleArchive.GetEntry('META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports')
            if ($null -eq $config -or $null -eq $importEntry) { throw 'Falta configuración segura M4' }
            $reader = [IO.StreamReader]::new($importEntry.Open())
            try { $importText = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $expectedConfigurations = @(
                'com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration',
                'com.prisaly.m4.infrastructure.config.M4ProgramacionConsultaAutoConfiguration'
            )
            $actualConfigurations = @($importText -split '\r?\n' | Where-Object { $_.Trim() })
            if (@(Compare-Object $expectedConfigurations $actualConfigurations).Count -ne 0) {
                throw 'Registro de autoconfiguración no válido'
            }
            foreach ($configurationName in $expectedConfigurations) {
                if ($null -eq $moduleArchive.GetEntry($configurationName.Replace('.', '/') + '.class')) {
                    throw "Falta clase de autoconfiguración: $configurationName"
                }
            }
            if ($stubs.Count -ne 0 -or $directStubs.Count -ne 0 -or $directV010.Count -ne 0 -or $v010.Count -ne 1) {
                throw 'Stubs o duplicación de V010 en paquete productivo'
            }
            $findings += "SHA256 M4 local/anidado: $nestedHash (idénticos)"
            $findings += "StubAdapter/TestM4Application: app=$($directStubs.Count), módulo M4=$($stubs.Count)"
            $findings += "V010: app=$($directV010.Count), módulo M4=$($v010.Count)"
            $findings += 'M4ProviderAutoConfiguration y AutoConfiguration.imports: presentes y verificados'
            $findings += 'Paquete del checkout M4 actual; no incorpora el nuevo develop.'
        } finally { $moduleArchive.Dispose() }
    } finally { $moduleBytes.Dispose() }
} finally { $appArchive.Dispose() }
$evidencePath = if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    Join-Path $PSScriptRoot 'evidence'
} else {
    [IO.Path]::GetFullPath($EvidenceDirectory)
}
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null
[IO.File]::WriteAllLines((Join-Path $evidencePath 'package-check.txt'), $findings, [Text.UTF8Encoding]::new($false))
$findings
