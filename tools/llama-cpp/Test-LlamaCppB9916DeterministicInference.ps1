[CmdletBinding()]
param(
    [string] $CacheDirectory,
    [ValidateRange(2, 10)]
    [int] $RepeatCount = 2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [Parameter(Mandatory = $true)]
        [string[]] $ArgumentList,
        [Parameter(Mandatory = $true)]
        [string] $WorkingDirectory,
        [ValidateRange(1, 600)]
        [int] $TimeoutSeconds = 120
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $ArgumentList) {
        [void] $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill($true)
        $process.WaitForExit()
        throw "$([System.IO.Path]::GetFileName($FilePath)) timed out after " +
            "$TimeoutSeconds seconds"
    }

    [pscustomobject]@{
        exitCode = $process.ExitCode
        stdout = $stdoutTask.GetAwaiter().GetResult()
        stderr = $stderrTask.GetAwaiter().GetResult()
    }
}

function Get-StringSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Invoke-DeterministicInference {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo] $Runner,
        [Parameter(Mandatory = $true)]
        [string] $ModelPath,
        [Parameter(Mandatory = $true)]
        [string] $Prompt,
        [Parameter(Mandatory = $true)]
        [int] $Runs
    )

    $arguments = @(
        "-m",
        $ModelPath,
        "--offline",
        "-p",
        $Prompt,
        "-n",
        "8",
        "-s",
        "424242",
        "--temp",
        "0",
        "-t",
        "1",
        "-tb",
        "1",
        "-ngl",
        "0",
        "-c",
        "128",
        "--no-display-prompt",
        "--no-warmup",
        "--no-mmap",
        "-lv",
        "1"
    )

    $outputs = foreach ($runNumber in 1..$Runs) {
        $run = Invoke-NativeCapture `
            -FilePath $Runner.FullName `
            -ArgumentList $arguments `
            -WorkingDirectory $Runner.DirectoryName
        if ($run.exitCode -ne 0) {
            throw "$($Runner.Name) deterministic inference run $runNumber failed " +
                "with exit code $($run.exitCode): $($run.stderr)"
        }

        $normalized = $run.stdout.Replace("`r`n", "`n").Trim()
        if ([string]::IsNullOrWhiteSpace($normalized)) {
            throw "$($Runner.Name) deterministic inference run $runNumber produced no output"
        }
        if ($normalized.Contains([char] 27)) {
            throw "$($Runner.Name) deterministic inference output contains terminal escapes"
        }
        $normalized
    }

    $uniqueOutputs = @($outputs | Select-Object -Unique)
    if ($uniqueOutputs.Count -ne 1) {
        throw "$($Runner.Name) produced different output across fixed-input runs"
    }

    [pscustomobject]@{
        runs = $Runs
        withinReleaseDeterministic = $true
        outputBytes = [System.Text.Encoding]::UTF8.GetByteCount($uniqueOutputs[0])
        outputSha256 = Get-StringSha256 -Value $uniqueOutputs[0]
        output = $uniqueOutputs[0]
    }
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$candidateConfigPath = Join-Path $PSScriptRoot "llama_cpp_b9916_comparison.json"
$activeConfigPath = Join-Path $PSScriptRoot "llama_cpp_active_defaults.json"
$allowlistPath = Join-Path $PSScriptRoot "approved_models.json"
$candidateConfig = Get-Content -LiteralPath $candidateConfigPath -Raw | ConvertFrom-Json
$activeConfig = Get-Content -LiteralPath $activeConfigPath -Raw | ConvertFrom-Json
$allowlist = Get-Content -LiteralPath $allowlistPath -Raw | ConvertFrom-Json

if (-not $candidateConfig.policy.verifySha256 -or
    -not $candidateConfig.policy.historicalComparisonOnly -or
    -not $candidateConfig.policy.notApprovedAsActiveBaseline) {
    throw "b9916 must remain a checksummed historical comparison, not an active baseline"
}
if ($candidateConfig.policy.packageIntoAndroidApp -or
    $candidateConfig.policy.androidRuntimeDefault -ne "soll-backend-route") {
    throw "b9916 comparison policy must not change the Android runtime"
}

if (-not $CacheDirectory) {
    $CacheDirectory = Join-Path $repoRoot $candidateConfig.policy.downloadCache
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

$candidateCache = Join-Path $cachePath "b9916"
New-Item -ItemType Directory -Force -Path $candidateCache | Out-Null
$candidatePackage = $candidateConfig.target.package
$candidateArchive = Join-Path $candidateCache $candidatePackage.asset
if (-not (Test-Path -LiteralPath $candidateArchive) -or
    (Get-Item -LiteralPath $candidateArchive).Length -ne [long] $candidatePackage.bytes) {
    $downloadUri = [Uri]::new(
        $candidateConfig.release.downloadBaseUrl + $candidatePackage.asset
    )
    Invoke-WebRequest -Uri $downloadUri -OutFile $candidateArchive -Headers @{
        "User-Agent" = "Soll-llama-cpp-b9916-deterministic-smoke"
    }
}
$candidateBytes = (Get-Item -LiteralPath $candidateArchive).Length
$candidateSha256 = (Get-FileHash -LiteralPath $candidateArchive -Algorithm SHA256).
    Hash.ToLowerInvariant()
if ($candidateBytes -ne [long] $candidatePackage.bytes) {
    throw "Size mismatch for $($candidatePackage.asset): $candidateBytes"
}
if ($candidateSha256 -ne $candidatePackage.sha256) {
    throw "SHA-256 mismatch for $($candidatePackage.asset): $candidateSha256"
}

$candidateExtract = Join-Path $candidateCache "extract-windows-x64-cpu"
$candidateRunner = Get-ChildItem -LiteralPath $candidateExtract -Recurse -File `
    -Filter $candidateConfig.target.inferenceExecutable -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $candidateRunner) {
    New-Item -ItemType Directory -Force -Path $candidateExtract | Out-Null
    Expand-Archive -LiteralPath $candidateArchive -DestinationPath $candidateExtract -Force
    $candidateRunner = Get-ChildItem -LiteralPath $candidateExtract -Recurse -File `
        -Filter $candidateConfig.target.inferenceExecutable |
        Select-Object -First 1
}
if (-not $candidateRunner) {
    throw "b9916 archive does not contain $($candidateConfig.target.inferenceExecutable)"
}
$candidateVersion = Invoke-NativeCapture `
    -FilePath $candidateRunner.FullName `
    -ArgumentList @("--version") `
    -WorkingDirectory $candidateRunner.DirectoryName
if ($candidateVersion.exitCode -ne 0) {
    throw "b9916 llama-completion --version failed with exit code $($candidateVersion.exitCode)"
}
$candidateVersionText = ($candidateVersion.stdout + $candidateVersion.stderr).Trim()
if ($candidateVersionText -notmatch $candidateConfig.target.versionPattern) {
    throw "Unexpected b9916 executable version: $candidateVersionText"
}

$activeCache = Join-Path $cachePath "active"
$activeReleaseSmokeScript = Join-Path $PSScriptRoot "Test-LlamaCppActiveRelease.ps1"
$activeReleaseSmokeJson = @(
    & $activeReleaseSmokeScript -Target "windows-x64-cpu" -CacheDirectory $activeCache
) -join "`n"
$activeReleaseSmoke = $activeReleaseSmokeJson | ConvertFrom-Json
if ($activeReleaseSmoke.smoke -ne "cli-and-server-version-executed") {
    throw "Active llama.cpp release smoke did not execute the CLI and server"
}
$activeRunner = Get-ChildItem -LiteralPath (Join-Path $activeCache "extract-windows-x64-cpu") `
    -Recurse -File -Filter $candidateConfig.target.inferenceExecutable |
    Select-Object -First 1
if (-not $activeRunner) {
    throw "Active llama-completion is missing after the release smoke"
}
$activeTarget = @($activeConfig.targets | Where-Object id -eq "windows-x64-cpu")
if ($activeTarget.Count -ne 1) {
    throw "Active config must define exactly one Windows x64 CPU target"
}
$activeVersion = Invoke-NativeCapture `
    -FilePath $activeRunner.FullName `
    -ArgumentList @("--version") `
    -WorkingDirectory $activeRunner.DirectoryName
if ($activeVersion.exitCode -ne 0) {
    throw "Active llama-completion --version failed with exit code $($activeVersion.exitCode)"
}
$activeVersionText = ($activeVersion.stdout + $activeVersion.stderr).Trim()
if ($activeVersionText -notmatch $activeTarget[0].versionPattern) {
    throw "Unexpected active executable version: $activeVersionText"
}

$approvedUse = [string] $candidateConfig.policy.approvedModelUse
$modelMatches = @($allowlist.models | Where-Object {
    @($_.approvedUses) -contains $approvedUse
})
if ($modelMatches.Count -ne 1) {
    throw "Expected exactly one model approved for $approvedUse"
}
$modelConfig = $modelMatches[0]
$modelDirectory = Join-Path $cachePath "models"
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
$modelPath = Join-Path $modelDirectory $modelConfig.fileName
if (-not (Test-Path -LiteralPath $modelPath) -or
    (Get-Item -LiteralPath $modelPath).Length -ne [long] $modelConfig.bytes) {
    Invoke-WebRequest -Uri ([Uri]::new($modelConfig.sourceUrl)) -OutFile $modelPath -Headers @{
        "User-Agent" = "Soll-llama-cpp-b9916-deterministic-smoke"
    }
}
$provenanceScript = Join-Path $PSScriptRoot "Test-LlamaCppModelProvenance.ps1"
$verifiedModel = & $provenanceScript -ModelPath $modelPath -AllowlistPath $allowlistPath
if ((Get-Item -LiteralPath $verifiedModel.modelPath).Length -ne [long] $modelConfig.bytes) {
    throw "Approved deterministic smoke model has an unexpected byte count"
}

$prompt = "Soll deterministic inference smoke: one local CPU path."
$candidateInference = Invoke-DeterministicInference `
    -Runner $candidateRunner `
    -ModelPath $verifiedModel.modelPath `
    -Prompt $prompt `
    -Runs $RepeatCount
$activeInference = Invoke-DeterministicInference `
    -Runner $activeRunner `
    -ModelPath $verifiedModel.modelPath `
    -Prompt $prompt `
    -Runs $RepeatCount

[pscustomobject]@{
    sourceRelease = $candidateConfig.release.tag
    fixCommit = $candidateConfig.release.commit
    pullRequest = $candidateConfig.release.pullRequest
    activeRelease = $activeConfig.release.tag
    activeCommit = $activeConfig.release.commit
    model = $verifiedModel.fileName
    modelBytes = (Get-Item -LiteralPath $verifiedModel.modelPath).Length
    modelSha256 = $verifiedModel.sha256
    modelRevision = $verifiedModel.revision
    prompt = $prompt
    tokenBudget = 8
    seed = 424242
    temperature = 0
    threads = 1
    batchThreads = 1
    gpuLayers = 0
    candidate = [pscustomobject]@{
        executable = $candidateRunner.Name
        version = $candidateVersionText
        archive = $candidatePackage.asset
        archiveBytes = $candidateBytes
        archiveSha256 = $candidateSha256
        runs = $candidateInference.runs
        withinReleaseDeterministic = $candidateInference.withinReleaseDeterministic
        outputBytes = $candidateInference.outputBytes
        outputSha256 = $candidateInference.outputSha256
    }
    active = [pscustomobject]@{
        executable = $activeRunner.Name
        version = $activeVersionText
        archive = $activeReleaseSmoke.packages[0].asset
        archiveBytes = $activeReleaseSmoke.packages[0].bytes
        archiveSha256 = $activeReleaseSmoke.packages[0].sha256
        runs = $activeInference.runs
        withinReleaseDeterministic = $activeInference.withinReleaseDeterministic
        outputBytes = $activeInference.outputBytes
        outputSha256 = $activeInference.outputSha256
    }
    crossReleaseOutputMatch = (
        $candidateInference.outputSha256 -eq $activeInference.outputSha256
    )
    inferenceRuns = $candidateInference.runs + $activeInference.runs
    allRunsExitCodeZero = $true
    packageIntoAndroidApp = $false
    androidRuntimeDefault = "soll-backend-route"
} | ConvertTo-Json -Depth 5
