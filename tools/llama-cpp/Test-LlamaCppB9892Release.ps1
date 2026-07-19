[CmdletBinding()]
param(
    [string[]] $Target,
    [string] $CacheDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$configPath = Join-Path $PSScriptRoot "llama_cpp_b9892_defaults.json"
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json

if (-not $Target -or $Target.Count -eq 0) {
    $Target = @($config.policy.defaultSmokeTargets)
}
if (-not $CacheDirectory) {
    $CacheDirectory = Join-Path $repoRoot $config.policy.downloadCache
}
if (-not [System.IO.Path]::IsPathFullyQualified($CacheDirectory)) {
    $CacheDirectory = Join-Path $repoRoot $CacheDirectory
}

$cachePath = [System.IO.Path]::GetFullPath($CacheDirectory)
$repoPrefix = $repoRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
if (-not $cachePath.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "CacheDirectory must stay inside the repository: $cachePath"
}
New-Item -ItemType Directory -Force -Path $cachePath | Out-Null

$results = foreach ($targetId in $Target) {
    $targetConfig = @($config.targets | Where-Object id -eq $targetId)
    if ($targetConfig.Count -ne 1) {
        throw "Unknown or duplicate target '$targetId'"
    }
    $targetConfig = $targetConfig[0]
    $packageResults = foreach ($package in @($targetConfig.packages)) {
        $archivePath = Join-Path $cachePath $package.asset
        if (-not (Test-Path -LiteralPath $archivePath) -or
            (Get-Item -LiteralPath $archivePath).Length -ne [long] $package.bytes) {
            $downloadUri = [Uri]::new($config.release.downloadBaseUrl + $package.asset)
            Invoke-WebRequest -Uri $downloadUri -OutFile $archivePath -Headers @{
                "User-Agent" = "Soll-llama-cpp-release-smoke"
            }
        }

        $actualBytes = (Get-Item -LiteralPath $archivePath).Length
        $actualSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualBytes -ne [long] $package.bytes) {
            throw "Size mismatch for $($package.asset): $actualBytes"
        }
        if ($actualSha256 -ne $package.sha256) {
            throw "SHA-256 mismatch for $($package.asset): $actualSha256"
        }

        $entryCount = if ($package.asset.EndsWith(".zip")) {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
            try {
                $zip.Entries.Count
            } finally {
                $zip.Dispose()
            }
        } else {
            $entries = @(& tar -tzf $archivePath)
            if ($LASTEXITCODE -ne 0) {
                throw "tar integrity check failed for $($package.asset)"
            }
            $entries.Count
        }
        if ($entryCount -eq 0) {
            throw "Archive is empty: $($package.asset)"
        }

        [pscustomobject]@{
            asset = $package.asset
            bytes = $actualBytes
            sha256 = $actualSha256
            archiveEntries = $entryCount
        }
    }

    $smoke = "archive-integrity-only"
    $version = $null
    $binaryCount = $null
    $primaryArchivePath = Join-Path $cachePath $targetConfig.packages[0].asset

    if ($targetId -eq "windows-x64-cpu" -and
        [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
            [System.Runtime.InteropServices.OSPlatform]::Windows
        ) -and
        [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq
            [System.Runtime.InteropServices.Architecture]::X64) {
        $extractPath = Join-Path $cachePath "extract-windows-x64-cpu"
        $cliPath = Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter "llama-cli.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if (-not $cliPath) {
            New-Item -ItemType Directory -Force -Path $extractPath | Out-Null
            Expand-Archive -LiteralPath $primaryArchivePath -DestinationPath $extractPath -Force
            $cliPath = Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter "llama-cli.exe" |
                Select-Object -First 1
        }
        $serverPath = Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter "llama-server.exe" |
            Select-Object -First 1
        if (-not $cliPath -or -not $serverPath) {
            throw "Windows archive does not contain llama-cli.exe and llama-server.exe"
        }
        Push-Location $cliPath.DirectoryName
        try {
            $cliVersion = @(& $cliPath.FullName --version 2>&1)
            if ($LASTEXITCODE -ne 0) {
                throw "llama-cli --version failed with exit code $LASTEXITCODE"
            }
            $serverVersion = @(& $serverPath.FullName --version 2>&1)
            if ($LASTEXITCODE -ne 0) {
                throw "llama-server --version failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
        $version = ($cliVersion + $serverVersion) -join " | "
        if ($version -notmatch "version: 9892 \(ee445f93d\)") {
            throw "Unexpected executable version: $version"
        }
        $smoke = "cli-and-server-version-executed"
    }

    if ($targetId -eq "android-arm64-cpu") {
        $extractPath = Join-Path $cachePath "extract-android-arm64-cpu"
        $cliPath = Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter "llama-cli" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if (-not $cliPath) {
            New-Item -ItemType Directory -Force -Path $extractPath | Out-Null
            & tar -xzf $primaryArchivePath -C $extractPath
            if ($LASTEXITCODE -ne 0) {
                throw "Android archive extraction failed"
            }
        }
        foreach ($requiredName in @("llama-cli", "llama-server", "libllama.so")) {
            $requiredFile = Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter $requiredName |
                Select-Object -First 1
            if (-not $requiredFile) {
                throw "Android archive does not contain $requiredName"
            }
        }
        $binaries = @(Get-ChildItem -LiteralPath $extractPath -Recurse -File |
            Where-Object Name -ne "LICENSE")
        $invalid = foreach ($file in $binaries) {
            $stream = [System.IO.File]::OpenRead($file.FullName)
            try {
                $header = [byte[]]::new(20)
                $read = $stream.Read($header, 0, $header.Length)
            } finally {
                $stream.Dispose()
            }
            $valid = $read -eq 20 -and
                $header[0] -eq 0x7F -and $header[1] -eq 0x45 -and
                $header[2] -eq 0x4C -and $header[3] -eq 0x46 -and
                $header[4] -eq 2 -and $header[5] -eq 1 -and
                $header[18] -eq 0xB7 -and $header[19] -eq 0
            if (-not $valid) {
                $file.Name
            }
        }
        if (@($invalid).Count -ne 0 -or $binaries.Count -eq 0) {
            throw "Android archive contains invalid ELF64 AArch64 files: $($invalid -join ', ')"
        }
        $binaryCount = $binaries.Count
        $smoke = "archive-and-elf64-aarch64-validated"
    }

    [pscustomobject]@{
        target = $targetId
        packages = @($packageResults)
        smoke = $smoke
        version = $version
        binaryCount = $binaryCount
    }
}

$results | ConvertTo-Json -Depth 6
