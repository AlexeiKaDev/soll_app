---
title: TEE usage for confidential LLM inference in Soll
task_id: 810413622d7845debcea6bf265340ac4
source_ref: insight/61caa564515a
review_status: knowledge_note_added_pilot_deferred
scope: confidential computing for server-side LLM inference
---

# TEE usage for confidential LLM inference in Soll

## Decision

Treat hardware-rooted confidential computing as a future server-side inference
option, not as an Android feature or a production security claim. Soll should
open a TEE pilot only for a named workload whose prompts or model assets need
protection from the inference host. The first pilot must use synthetic data,
disable tool execution and compare the same model on TEE and non-TEE workers.

No production TEE or attestation integration is authorized by this note. A TEE
can reduce infrastructure trust, but it does not make a model safe and it does
not prove that performance overhead is negligible. Both properties require
separate evidence.

## Source boundary

The task names the monitored source
`monitored/nvidia-technical-blog/20260706-080044-hardware-rooted-ai-security-that-won-t-slow-you--c2f1af06.md`
and the application "Confidential Computing for LLM inference". That source
file is not present in this isolated worktree. Consequently, this note preserves
the supplied signal but does not repeat vendor architecture, compatibility or
performance claims that cannot be audited locally.

## What a TEE adds

A TEE protects data in use by isolating an executing workload and its memory
from software outside the trusted boundary. Remote attestation can let a
verifier check the workload identity and security state before a key broker
releases prompt or model-decryption keys.

This complements, rather than replaces, the other encryption boundaries:

| Data state | Primary control | TEE contribution |
| --- | --- | --- |
| At rest | encrypted storage and controlled key retention | seal or release keys only to an approved workload measurement |
| In transit | authenticated TLS and request authorization | bind a session key to fresh attestation when end-to-end confidentiality is required |
| In use | process/host access controls | isolate plaintext prompts, KV cache, model weights and outputs from the untrusted host layer |

A TEE does not replace TLS, client authentication, authorization, retention
limits or encrypted storage. It also does not protect against prompt injection,
a malicious or vulnerable approved model image, sensitive output disclosure,
traffic metadata, denial of service, compromised client endpoints, or unsafe
side effects after inference.

## Current Soll fit

Four current seams were audited in this repository:

1. `SollGateway.askModelChat(...)` and `SollRepository.askModelChat(...)` keep
   model requests backend-mediated, which is the correct insertion point for a
   future confidential server worker.
2. `SecurePayloadEnvelopeRequest` supports an application AES-256-GCM envelope.
   It does not carry an attestation result, workload measurement or a key bound
   to an attested inference session, so it is not proof of confidential
   execution.
3. `EncryptedSharedPreferences` uses an Android Keystore master key for local
   settings when initialization succeeds, with a regular preferences fallback.
   This is device-side storage behavior, not evidence that remote inference ran
   in hardware-isolated memory.
4. Current Android production/build definitions expose no named TEE,
   confidential-computing or remote-attestation client contract. The existing
   server route can therefore remain unchanged for a server-only benchmark;
   Android API changes require a separately approved contract.

The immediate value is knowledge and a testable adoption boundary.
Actual TEE inference runs completed by this task: **0**. Measured latency,
throughput, security or cost improvement from TEE execution: **0**.

## Recommended server-first architecture

```text
Android approval/chat client
  -> authenticated Soll gateway and policy router
  -> attestation verifier + short-lived key broker
  -> approved confidential inference worker
  -> metadata-only evidence receipt
  -> existing output/tool-action policy gates
```

The control plane should keep vendor-specific attestation parsing out of the
Android application. Before dispatch, it verifies a fresh, nonce-bound
attestation against an allowlisted workload measurement and release policy. A
key broker then grants a short-lived key only to that approved workload. The
data plane decrypts prompts inside the measured boundary, runs inference and
returns the output plus a non-sensitive evidence receipt.

For a later high-assurance mode that must also hide plaintext from the Soll
gateway, use a versioned confidential-session contract: bind an ephemeral
encryption key to independently verifiable attestation and encrypt on the
client to that key. This is a larger protocol change and must not be implied by
ordinary TLS or by the current payload envelope.

The evidence receipt should be safe to persist and include only:

- confidential mode requested and achieved;
- TEE technology/profile and verifier policy version;
- approved workload measurement or stable policy identifier;
- attestation issued/expiry times and replay-protected request/session id;
- model/image version, with no model key or prompt content;
- fallback decision and reason;
- time to first token, total latency, generated token count and worker errors.

For requests marked confidential, silent non-TEE fallback is forbidden. A
failed or stale attestation must fail closed with a visible retry/reject reason.
An explicitly non-confidential request may use the ordinary route according to
the existing router policy.

## Safe benchmark plan

Use one fixed, synthetic and non-sensitive prompt fixture with tools disabled.
Run the same model, weights, precision, accelerator, prompt set, output limit,
batch/concurrency and warm-up policy in both modes. Record at least 30 warmed
requests per mode and five cold-start cycles; retain aggregate metrics and error
receipts, never prompt bodies or secrets.

Measure:

- TTFT p50/p95 and end-to-end latency p50/p95;
- output tokens per second and successful requests per minute;
- cold start, attestation and key-release time separately;
- peak accelerator/host memory where the platform exposes it safely;
- maximum stable concurrency and timeout/error rate;
- cost per successful request and capacity loss against the baseline;
- number of stale/replayed/invalid attestations accepted (target: zero);
- number of confidential requests silently downgraded (target: zero).

The workload owner must declare acceptable latency, throughput, availability
and cost deltas before running the benchmark. Results that omit the ordinary
baseline cannot substantiate a "won't slow you down" decision.

## Eight promotion gates

1. **Threat model.** Name the protected prompt/model assets, the untrusted host
   roles, the accepted residual risks and why ordinary isolation is insufficient.
2. **Measured workload.** Pin the image, model, runtime, accelerator and fixture;
   prohibit production prompts, ambient credentials and tool execution in the
   first pilot.
3. **Attestation verification.** Validate signature chain, nonce, freshness,
   security/debug state and an allowlisted workload measurement; reject replay
   and unknown measurements.
4. **Key and data lifecycle.** Release short-lived keys only after policy passes;
   define prompt/KV/output retention, crash-dump behavior, zeroization and key
   revocation.
5. **Boundary audit.** Document plaintext exposure at the Android client,
   gateway, logs, queues, caches, external retrieval and response delivery;
   remove prompt content from telemetry.
6. **Output and action safety.** Keep model output untrusted and route every tool
   suggestion through the existing authorization, confirmation and audit gates.
7. **Performance and reliability.** Pass the predeclared TTFT, throughput,
   concurrency, failure-rate, capacity and cost budgets against the identical
   non-TEE baseline.
8. **Evidence, fallback and rollback.** Persist verifiable metadata-only
   receipts, alert on attestation failure, forbid silent downgrade for
   confidential requests and demonstrate rollback without losing audit history.

Promotion requires all eight gates. A passing benchmark supports only the
tested workload/image/hardware combination; it is not a general TEE or vendor
certification.

## Value decision

The source signal produced one durable TEE knowledge note, an audit of four
current Soll seams and eight measurable promotion gates. This is useful future
security groundwork but produces no immediate runtime gain: no TEE worker,
attestation path, production prompt or Android production file was changed.
