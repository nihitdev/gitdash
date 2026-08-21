[CmdletBinding()]
param(
    [string] $Prefix = (Join-Path $env:LOCALAPPDATA "Programs\GitDash"),
    [string] $Version = "0.2.0",
    [switch] $NoPath,
    [switch] $Uninstall,
    [long] $WaitForProcessId = 0
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($WaitForProcessId -gt 0) {
    Wait-Process -Id $WaitForProcessId -ErrorAction SilentlyContinue
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 26 is required but 'java' was not found on PATH. Install Eclipse Temurin 26 and try again."
}

$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("gitdash-install-" + [Guid]::NewGuid().ToString("N"))
$archive = Join-Path $temporaryDirectory "gitdash.zip"
$downloadUrl = "https://github.com/nihitdev/gitdash/releases/download/v$Version/gitdash-$Version.zip"
$distributionDirectory = Join-Path $temporaryDirectory "gitdash-$Version"
$installDirectory = Join-Path $Prefix "lib\gitdash"
$binDirectory = Join-Path $Prefix "bin"

if ($Uninstall) {
    $commandPath = Join-Path $binDirectory "gitdash.cmd"
    if (Test-Path -LiteralPath $commandPath) { Remove-Item -LiteralPath $commandPath -Force }
    if (Test-Path -LiteralPath $installDirectory) { Remove-Item -LiteralPath $installDirectory -Recurse -Force }
    if (-not $NoPath) {
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        $entries = @($userPath -split ";" | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_) -and $_.TrimEnd("\") -ine $binDirectory.TrimEnd("\")
        })
        [Environment]::SetEnvironmentVariable("Path", ($entries -join ";"), "User")
    }
    Write-Host "Uninstalled GitDash from $Prefix"
    exit 0
}

try {
    New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
    Write-Host "Downloading GitDash $Version..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archive -UseBasicParsing
    $checksumFile = Join-Path $temporaryDirectory "SHA256SUMS"
    $checksumUrl = "https://github.com/nihitdev/gitdash/releases/download/v$Version/SHA256SUMS"
    Invoke-WebRequest -Uri $checksumUrl -OutFile $checksumFile -UseBasicParsing
    $expected = Get-Content -LiteralPath $checksumFile | ForEach-Object {
        if ($_ -match "^([0-9a-fA-F]{64})\s+gitdash-$([regex]::Escape($Version))\.zip$") { $Matches[1] }
    } | Select-Object -First 1
    if (-not $expected) { throw "The release checksum file does not contain the Windows archive." }
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    if ($actual -ine $expected) { throw "GitDash archive checksum verification failed." }
    Expand-Archive -Path $archive -DestinationPath $temporaryDirectory -Force

    $launcher = Join-Path $distributionDirectory "bin\gitdash.bat"
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw "The downloaded archive does not contain the expected GitDash launcher."
    }

    if (Test-Path -LiteralPath $installDirectory) {
        Remove-Item -LiteralPath $installDirectory -Recurse -Force
    }
    New-Item -ItemType Directory -Path (Split-Path $installDirectory -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $distributionDirectory -Destination $installDirectory -Recurse

    New-Item -ItemType Directory -Path $binDirectory -Force | Out-Null
    $command = "@echo off`r`ncall `"%~dp0..\lib\gitdash\bin\gitdash.bat`" %*`r`n"
    Set-Content -LiteralPath (Join-Path $binDirectory "gitdash.cmd") -Value $command -Encoding ASCII -NoNewline

    if (-not $NoPath) {
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        $entries = @($userPath -split ";" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if (-not ($entries | Where-Object { $_.TrimEnd("\") -ieq $binDirectory.TrimEnd("\") })) {
            $newPath = (@($entries) + $binDirectory) -join ";"
            [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        }
        if (-not (($env:Path -split ";") | Where-Object { $_.TrimEnd("\") -ieq $binDirectory.TrimEnd("\") })) {
            $env:Path = "$binDirectory;$env:Path"
        }
    }

    & (Join-Path $binDirectory "gitdash.cmd") --version
    Write-Host "Installed GitDash to $installDirectory"
    if ($NoPath) {
        Write-Host "Add $binDirectory to PATH to run 'gitdash' from any terminal."
    } else {
        Write-Host "Open a new terminal, then run: gitdash --help"
    }
} finally {
    if (Test-Path -LiteralPath $temporaryDirectory) {
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
    }
}
