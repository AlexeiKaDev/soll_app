[CmdletBinding()]
param(
    [string] $CacheDirectory
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
        [string] $WorkingDirectory
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
    $process.WaitForExit()

    [pscustomobject]@{
        exitCode = $process.ExitCode
        output = $stdoutTask.GetAwaiter().GetResult() + $stderrTask.GetAwaiter().GetResult()
    }
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$configPath = Join-Path $PSScriptRoot "llama_cpp_active_defaults.json"
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$activeRelease = [int] $config.release.tag.TrimStart("b")
$minimumFixRelease = [int] $config.policy.minimumChatTemplateFixRelease
if ($activeRelease -lt $minimumFixRelease) {
    throw "Active llama.cpp release predates the b9945 chat-template crash fix"
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
$modelMatches = @($allowlist.models | Where-Object purpose -eq "b9945-chat-template-smoke-only")
if ($modelMatches.Count -ne 1) {
    throw "Expected exactly one approved b9945 chat-template smoke model"
}
$modelConfig = $modelMatches[0]

$modelDirectory = Join-Path $cachePath "models"
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
$modelPath = Join-Path $modelDirectory $modelConfig.fileName
if (-not (Test-Path -LiteralPath $modelPath) -or
    (Get-Item -LiteralPath $modelPath).Length -ne [long] $modelConfig.bytes) {
    Invoke-WebRequest -Uri ([Uri]::new($modelConfig.sourceUrl)) -OutFile $modelPath -Headers @{
        "User-Agent" = "Soll-llama-cpp-chat-template-smoke"
    }
}

$provenanceScript = Join-Path $PSScriptRoot "Test-LlamaCppModelProvenance.ps1"
$verifiedModel = & $provenanceScript -ModelPath $modelPath -AllowlistPath $allowlistPath
if ((Get-Item -LiteralPath $verifiedModel.modelPath).Length -ne [long] $modelConfig.bytes) {
    throw "Approved smoke model has an unexpected byte count"
}

$templatePath = Join-Path $PSScriptRoot "soll-nonstandard-chat-template.jinja"
$template = Get-Content -LiteralPath $templatePath -Raw
foreach ($fragment in @(
    "<|soll_{{ message['role'] }}|>",
    "<|soll_end|>",
    "<|soll_assistant|>",
    "messages",
    "add_generation_prompt"
)) {
    if (-not $template.Contains($fragment)) {
        throw "Non-standard Soll chat template is missing: $fragment"
    }
}

$cli = Get-ChildItem -LiteralPath (Join-Path $cachePath "extract-windows-x64-cpu") `
    -Recurse -File -Filter "llama-cli.exe" |
    Select-Object -First 1
if (-not $cli) {
    throw "Pinned llama-cli is missing after the active release smoke"
}
$arguments = @(
    "-m",
    $verifiedModel.modelPath,
    "--jinja",
    "--chat-template-file",
    $templatePath,
    "-cnv",
    "-st",
    "-p",
    "Soll b9945 chat-template smoke",
    "-n",
    "1",
    "--no-display-prompt",
    "--no-warmup",
    "-lv",
    "4"
)
$run = Invoke-NativeCapture `
    -FilePath $cli.FullName `
    -ArgumentList $arguments `
    -WorkingDirectory $cli.DirectoryName
if ($run.exitCode -ne 0) {
    throw "llama-cli custom chat-template smoke failed with exit code $($run.exitCode): $($run.output)"
}
if ($run.output -notmatch "llama_server: model loaded") {
    throw "llama-cli output did not confirm that the approved GGUF model loaded"
}
if ($run.output -notmatch "chat template, example_format:.*<\|soll_system\|>") {
    throw "llama-cli output did not confirm use of the non-standard Soll chat template"
}
if ($run.output -match "SIGABRT|abort has been called") {
    throw "llama-cli aborted while applying the non-standard chat template"
}

[pscustomobject]@{
    release = $config.release.tag
    commit = $config.release.commit
    minimumChatTemplateFixRelease = "b$minimumFixRelease"
    model = $verifiedModel.fileName
    modelBytes = (Get-Item -LiteralPath $verifiedModel.modelPath).Length
    modelSha256 = $verifiedModel.sha256
    modelRevision = $verifiedModel.revision
    template = (Resolve-Path -LiteralPath $templatePath).Path
    templateSha256 = (Get-FileHash -LiteralPath $templatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    nonStandardTemplateApplied = $true
    modelLoaded = $true
    exitCode = $run.exitCode
} | ConvertTo-Json
