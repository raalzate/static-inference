#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$Java,
    [switch]$Dotnet,
    [Parameter(Position = 0)][string]$ProjectPath,
    [Parameter(Position = 1)][string]$OutputFile
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Show-Usage {
    @"
Usage: run-extractor.ps1 [-Java | -Dotnet] <project-path> <output-file>

Runs the appropriate static-inference analyzer (Java or .NET) against
<project-path> and writes the dependency graph JSON to <output-file>.

If neither -Java nor -Dotnet is given, the project type is auto-detected:
  - .NET   <- *.sln, *.csproj, *.fsproj or *.vbproj found in the path
  - Java   <- otherwise (pom.xml, build.gradle, src/, etc.)

Override the JAR / DLL location via environment variables:
  `$env:JAR_FILE   = 'C:\path\to\java-dependency-extractor.jar'
  `$env:DOTNET_DLL = 'C:\path\to\dotnet-analyzer.dll'

Examples:
  .\run-extractor.ps1 C:\projects\spring-app graph.json
  .\run-extractor.ps1 -Dotnet C:\projects\MySolution.sln graph.json
"@ | Write-Host
}

if (-not $ProjectPath -or -not $OutputFile) { Show-Usage; exit 1 }
if ($Java -and $Dotnet) { Write-Error '-Java and -Dotnet are mutually exclusive.'; exit 1 }
if (-not (Test-Path -LiteralPath $ProjectPath)) { Write-Error "Project path not found: $ProjectPath"; exit 1 }

# ── Resolve mode ──────────────────────────────────────────────
$Mode = if ($Java) { 'java' } elseif ($Dotnet) { 'dotnet' } else { '' }

if (-not $Mode) {
    $item = Get-Item -LiteralPath $ProjectPath
    if (-not $item.PSIsContainer) {
        $Mode = if ($item.Extension -in '.sln', '.csproj', '.fsproj', '.vbproj') { 'dotnet' } else { 'java' }
    } else {
        $hit = Get-ChildItem -LiteralPath $ProjectPath -Recurse -Depth 4 -File `
                -Include *.sln, *.csproj, *.fsproj, *.vbproj -ErrorAction SilentlyContinue |
                Select-Object -First 1
        $Mode = if ($hit) { 'dotnet' } else { 'java' }
    }
    Write-Host "Auto-detected project type: $Mode"
}

function Resolve-First {
    param([string[]]$Candidates)
    foreach ($c in $Candidates) {
        if ($c -and (Test-Path -LiteralPath $c -PathType Leaf)) { return (Resolve-Path -LiteralPath $c).Path }
    }
    return $null
}

# ── Java runner ──────────────────────────────────────────────
function Invoke-JavaExtractor {
    $jar = Resolve-First @(
        $env:JAR_FILE,
        (Join-Path $ScriptDir 'java\java-dependency-extractor.jar'),
        (Join-Path $ScriptDir 'target\java-dependency-extractor.jar')
    )
    if (-not $jar) {
        Write-Error "Java JAR not found. Set `$env:JAR_FILE or build with 'mvn clean package'."
        exit 1
    }

    Write-Host 'Running Java Dependency Extractor...'
    Write-Host "  JAR:     $jar"
    Write-Host "  Project: $ProjectPath"
    Write-Host "  Output:  $OutputFile`n"

    $javaExe = $null
    if ($env:JAVA_CMD) {
        $javaExe = $env:JAVA_CMD
    } elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    } elseif (Get-Command java -ErrorAction SilentlyContinue) {
        $javaExe = 'java'
    } else {
        Write-Error "No Java installation found. Set `$env:JAVA_CMD, `$env:JAVA_HOME or add java to PATH."
        exit 1
    }

    & $javaExe -jar $jar $ProjectPath $OutputFile
    return $LASTEXITCODE
}

# ── .NET runner ──────────────────────────────────────────────
function Invoke-DotnetExtractor {
    $dll = Resolve-First @(
        $env:DOTNET_DLL,
        (Join-Path $ScriptDir 'dotnet\dotnet-analyzer.dll')
    )
    if (-not $dll) {
        Write-Error ".NET analyzer DLL not found. Set `$env:DOTNET_DLL or rebuild distribution."
        exit 1
    }
    if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
        Write-Error "'dotnet' runtime not found. Install .NET 8 SDK/runtime."
        exit 1
    }

    Write-Host 'Running .NET Dependency Extractor...'
    Write-Host "  DLL:     $dll"
    Write-Host "  Project: $ProjectPath"
    Write-Host "  Output:  $OutputFile`n"

    & dotnet $dll $ProjectPath | Out-File -LiteralPath $OutputFile -Encoding utf8
    return $LASTEXITCODE
}

switch ($Mode) {
    'java'   { $exit = Invoke-JavaExtractor }
    'dotnet' { $exit = Invoke-DotnetExtractor }
    default  { Write-Error "Unknown mode '$Mode'"; exit 1 }
}

if ($exit -eq 0) {
    Write-Host "`nAnalysis complete!"
    Write-Host 'Generated files:'
    Get-Item -LiteralPath $OutputFile, 'output_architecture.json', 'output.json' -ErrorAction SilentlyContinue |
        ForEach-Object { '{0,12}  {1}' -f $_.Length, $_.Name } | Write-Host
    if (Test-Path -LiteralPath 'output_web.json') {
        Write-Host 'Web components detected:'
        Get-Item -LiteralPath 'output_web.json' | ForEach-Object { '{0,12}  {1}' -f $_.Length, $_.Name } | Write-Host
    }
} else {
    Write-Host "`nAnalysis failed with exit code $exit"
    exit $exit
}
