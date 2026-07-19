# llama.cpp verification tools

The active standalone verification baseline is `b10068`, which is newer than
the `b9917` UGM tokenizer security baseline. Verify the checksummed Windows and
Android release archives with:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppActiveRelease.ps1
```

The active release is not packaged into the Android app. Android continues to
use `soll-backend-route` by default.

GGUF loading is deny-by-default. `approved_models.json` is intentionally empty,
so no model can currently pass the provenance gate. A future entry must pin the
exact file name, SHA-256, HTTPS source URL and immutable source revision. The
only repository-sanctioned model entry point is:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Invoke-LlamaCppVerifiedModel.ps1 `
  -ModelPath D:\approved\model.gguf -- -p "smoke" -n 1
```

Direct model loading through the archived `b9892`, `b9895` or `b9898` binaries
is not an approved Soll workflow. Those manifests and scripts remain only as
historical release evidence.
