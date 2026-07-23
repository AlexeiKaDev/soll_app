# llama.cpp verification tools

The active standalone verification baseline is `b10068`, which is newer than
the `b9917` UGM tokenizer security baseline. Verify the checksummed Windows and
Android release archives with:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppActiveRelease.ps1
```

The active release is not packaged into the Android app. Android continues to
use `soll-backend-route` by default.

GGUF loading is deny-by-default. The allowlist contains one immutable
`ggml-org/tiny-llamas` fixture for the b9945 chat-template smoke only; it is
downloaded to the ignored build cache and is never packaged into the app.
Every entry must pin the exact file name, SHA-256, HTTPS source URL and
immutable source revision. The general repository-sanctioned model entry point
is:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Invoke-LlamaCppVerifiedModel.ps1 `
  -ModelPath D:\approved\model.gguf -- -p "smoke" -n 1
```

Direct model loading through the archived `b9892`, `b9895` or `b9898` binaries
is not an approved Soll workflow. Those manifests and scripts remain only as
historical release evidence.

The active b10068 baseline includes the b9945 chat-template crash fix. Run the
focused positive smoke with the approved tiny model and a non-standard Jinja
template:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9945ChatTemplate.ps1
```
