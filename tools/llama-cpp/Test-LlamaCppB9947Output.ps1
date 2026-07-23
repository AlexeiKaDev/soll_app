[CmdletBinding()]
param(
    [string] $CacheDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$configPath = Join-Path $PSScriptRoot "llama_cpp_active_defaults.json"
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$activeRelease = [int] $config.release.tag.TrimStart("b")
$minimumOutputFileRelease = [int] $config.policy.minimumOutputFileRelease
if ($activeRelease -lt $minimumOutputFileRelease) {
    throw "Active llama.cpp release predates the b9947 --output option"
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

$releaseSmokeScript = Join-Path $PSScriptRoot "Test-LlamaCppActiveRelease.ps1"
$releaseSmoke = @(& $releaseSmokeScript -Target "windows-x64-cpu" -CacheDirectory $cachePath)
if (-not ($releaseSmoke -join "`n").Contains('"smoke": "cli-and-server-version-executed"')) {
    throw "Pinned Windows llama.cpp release smoke did not execute the CLI and server"
}

$allowlistPath = Join-Path $repoRoot $config.policy.modelAllowlist
$allowlist = Get-Content -LiteralPath $allowlistPath -Raw | ConvertFrom-Json
$modelMatches = @($allowlist.models | Where-Object {
    @($_.approvedUses) -contains "b9947-output-file-smoke"
})
if ($modelMatches.Count -ne 1) {
    throw "Expected exactly one model approved for the b9947 output-file smoke"
}
$modelConfig = $modelMatches[0]

$modelDirectory = Join-Path $cachePath "models"
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
$modelPath = Join-Path $modelDirectory $modelConfig.fileName
if (-not (Test-Path -LiteralPath $modelPath) -or
    (Get-Item -LiteralPath $modelPath).Length -ne [long] $modelConfig.bytes) {
    Invoke-WebRequest -Uri ([Uri]::new($modelConfig.sourceUrl)) -OutFile $modelPath -Headers @{
        "User-Agent" = "Soll-llama-cpp-output-file-smoke"
    }
}

$provenanceScript = Join-Path $PSScriptRoot "Test-LlamaCppModelProvenance.ps1"
$verifiedModel = & $provenanceScript -ModelPath $modelPath -AllowlistPath $allowlistPath
if ((Get-Item -LiteralPath $verifiedModel.modelPath).Length -ne [long] $modelConfig.bytes) {
    throw "Approved output smoke model has an unexpected byte count"
}

$cli = Get-ChildItem -LiteralPath (Join-Path $cachePath "extract-windows-x64-cpu") `
    -Recurse -File -Filter "llama-cli.exe" |
    Select-Object -First 1
if (-not $cli) {
    throw "Pinned llama-cli is missing after the active release smoke"
}

$outputDirectory = Join-Path $cachePath "output-smoke"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$outputPath = Join-Path $outputDirectory "llama-cli-output.txt"
$prompt = "Soll output smoke: harmless local inference artifact."
$arguments = @(
    "-m",
    $verifiedModel.modelPath,
    "--offline",
    "-cnv",
    "-st",
    "--simple-io",
    "-p",
    $prompt,
    "-n",
    "1",
    "-s",
    "1",
    "--temp",
    "0",
    "--no-display-prompt",
    "--no-warmup",
    "-lv",
    "1",
    "--output",
    $outputPath
)

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $cli.FullName
$startInfo.WorkingDirectory = $cli.DirectoryName
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $false
$startInfo.RedirectStandardError = $false
foreach ($argument in $arguments) {
    [void] $startInfo.ArgumentList.Add($argument)
}

$process = [System.Diagnostics.Process]::Start($startInfo)
$process.WaitForExit()
if ($process.ExitCode -ne 0) {
    throw "llama-cli --output smoke failed with exit code $($process.ExitCode)"
}
if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
    throw "llama-cli exited successfully without creating the requested output file"
}

$outputFile = Get-Item -LiteralPath $outputPath
$outputContent = Get-Content -LiteralPath $outputFile.FullName -Raw
$normalizedOutput = $outputContent.Replace("`r`n", "`n")
$expectedPrefix = "User:`n$prompt`n`nAssistant:`n"
if (-not $normalizedOutput.StartsWith($expectedPrefix, [System.StringComparison]::Ordinal)) {
    throw "llama-cli output file does not contain the expected User/Assistant transcript"
}
$assistantContent = $normalizedOutput.Substring($expectedPrefix.Length).Trim()
if ([string]::IsNullOrWhiteSpace($assistantContent)) {
    throw "llama-cli output file does not contain generated assistant content"
}
if ($outputContent.Contains([char] 27)) {
    throw "llama-cli output file unexpectedly contains terminal escape sequences"
}

[pscustomobject]@{
    release = $config.release.tag
    commit = $config.release.commit
    minimumOutputFileRelease = "b$minimumOutputFileRelease"
    upstreamCommit = "3de7dd4c8f5d9806279249310b6c3db24a1a67ab"
    model = $verifiedModel.fileName
    modelBytes = (Get-Item -LiteralPath $verifiedModel.modelPath).Length
    modelSha256 = $verifiedModel.sha256
    modelRevision = $verifiedModel.revision
    prompt = $prompt
    outputPath = [System.IO.Path]::GetRelativePath(
        $repoRoot,
        $outputFile.FullName
    ).Replace("\", "/")
    outputBytes = $outputFile.Length
    outputSha256 = (Get-FileHash -LiteralPath $outputFile.FullName -Algorithm SHA256).
        Hash.ToLowerInvariant()
    stdoutParsed = $false
    userPromptPersisted = $true
    assistantContentPersisted = $true
    exitCode = $process.ExitCode
} | ConvertTo-Json
