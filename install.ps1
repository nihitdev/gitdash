[CmdletBinding()]
param(
    [string] $Prefix = (Join-Path $env:LOCALAPPDATA "Programs\GitDash"),
    [string] $Version = "0.1.0",
    [switch] $NoPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 26 is required but 'java' was not found on PATH. Install Eclipse Temurin 26 and try again."
}

$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("gitdash-install-" + [Guid]::NewGuid().ToString("N"))
$archive = Join-Path $temporaryDirectory "gitdash.zip"
$downloadUrl = "https://github.com/nihitdev/gitdash/releases/download/v$Version/gitdash-$Version.zip"
$distributionDirectory = Join-Path $temporaryDirectory "gitdash-$Version"
$installDirectory = Join-Path $Prefix "lib\gitdash"
$binDirectory = Join-Path $Prefix "bin"

try {
    New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
    Write-Host "Downloading GitDash $Version..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archive -UseBasicParsing
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
