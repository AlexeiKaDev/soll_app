[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModelPath,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $LlamaArgument
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot "llama_cpp_active_defaults.json") -Raw |
    ConvertFrom-Json
$provenanceScript = Join-Path $PSScriptRoot "Test-LlamaCppModelProvenance.ps1"
$verified = & $provenanceScript -ModelPath $ModelPath -AllowlistPath $config.policy.modelAllowlist

$cachePath = Join-Path $repoRoot $config.policy.downloadCache
$cli = Get-ChildItem -LiteralPath (Join-Path $cachePath "extract-windows-x64-cpu") `
    -Recurse -File -Filter "llama-cli.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $cli) {
    throw "Pinned llama-cli is missing; run Test-LlamaCppActiveRelease.ps1 first"
}

& $cli.FullName -m $verified.modelPath @LlamaArgument
exit $LASTEXITCODE
