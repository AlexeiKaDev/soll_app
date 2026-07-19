[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModelPath,
    [string] $AllowlistPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
if (-not $AllowlistPath) {
    $AllowlistPath = Join-Path $PSScriptRoot "approved_models.json"
}
if (-not [System.IO.Path]::IsPathFullyQualified($AllowlistPath)) {
    $AllowlistPath = Join-Path $repoRoot $AllowlistPath
}

$resolvedAllowlist = [System.IO.Path]::GetFullPath($AllowlistPath)
$repoPrefix = $repoRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedAllowlist.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "AllowlistPath must stay inside the repository: $resolvedAllowlist"
}
if (-not (Test-Path -LiteralPath $resolvedAllowlist -PathType Leaf)) {
    throw "Model allowlist does not exist: $resolvedAllowlist"
}

$model = Get-Item -LiteralPath $ModelPath -ErrorAction Stop
if ($model.Extension -cne ".gguf") {
    throw "Only .gguf model files can pass the llama.cpp provenance gate"
}

$allowlist = Get-Content -LiteralPath $resolvedAllowlist -Raw | ConvertFrom-Json
if ($allowlist.policy -ne "deny_unlisted") {
    throw "Model allowlist must use deny_unlisted policy"
}

$actualSha256 = (Get-FileHash -LiteralPath $model.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$matches = @($allowlist.models | Where-Object {
    $_.fileName -ceq $model.Name -and $_.sha256 -ceq $actualSha256
})
if ($matches.Count -ne 1) {
    throw "GGUF model is not approved by exact file name and SHA-256: $($model.Name)"
}

$approved = $matches[0]
$sourceUri = $null
if (-not [Uri]::TryCreate([string] $approved.sourceUrl, [UriKind]::Absolute, [ref] $sourceUri) -or
    $sourceUri.Scheme -ne "https") {
    throw "Approved GGUF entry requires an absolute HTTPS sourceUrl"
}
if ([string]::IsNullOrWhiteSpace([string] $approved.revision)) {
    throw "Approved GGUF entry requires an immutable source revision"
}

[pscustomobject]@{
    approved = $true
    modelPath = $model.FullName
    fileName = $model.Name
    sha256 = $actualSha256
    sourceUrl = $sourceUri.AbsoluteUri
    revision = [string] $approved.revision
}
