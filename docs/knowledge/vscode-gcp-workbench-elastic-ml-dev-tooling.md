---
title: VS Code and GCP Workbench for elastic ML development
task_id: d4db390d37d048bc9f76d51071b42479
source_ref: insight/c311bd90fa93
review_status: dev_tooling_note_added_cloud_pilot_deferred
scope: VS Code + GCP Workbench integration
---

# VS Code and GCP Workbench for elastic ML development

## Decision

Keep VS Code with GCP Workbench as a proposal-only desktop/server development
option for Soll ML experiments. It is not an Android feature, a production
runtime change, or authorization to create a Google Cloud project, enable an
API, provision a Workbench instance or TPU, install an extension, authenticate
an account, upload data, or start a training job.

The source signal says that a TPU was terminated during training and recovered
in seconds. Treat that as an unverified source claim, not as a platform SLA or
as evidence that an arbitrary Soll workload is elastic. VS Code supplies an
editor and remote-development surface; recovery still depends on the training
framework, topology, checkpoint design, persistent storage, orchestration and
failure policy used by the exact workload.

## Source boundary

The task names the monitored source
`monitored/google-developers-blog/20260709-204007-ml-development-in-vs-code-with-google-cloud-powe-b1594323.md`
and the application `VS Code + GCP Workbench integration`. The monitored file
is not vendored in this isolated worktree. This note therefore preserves only
the task-supplied signal and does not repeat unverified vendor commands,
extension behavior, compatibility, recovery time, pricing or performance
claims.

Before a pilot, an operator must review the current official documentation for
the selected Google Cloud region, Vertex AI Workbench mode, Cloud TPU
generation/topology, framework/runtime image and supported VS Code connection
method. Product names and setup flows can change; this note is a decision gate,
not a setup recipe.

## Current Soll fit

Four current repository seams were audited:

1. `soll_app` is an Android client. Its Gradle build definitions contain no
   Google Cloud Workbench, Cloud TPU or remote-training dependency, and none is
   needed for a developer workstation pilot.
2. `SollGateway.askModelChat(...)` keeps AI execution behind a server boundary.
   A future experiment can publish a summary or artifact through existing Soll
   services without giving the phone cloud credentials or direct TPU control.
3. No task-owned GCP project, billing account, quota, region, Workbench
   instance, TPU topology, dataset or reproducible training workload is defined
   in this repository.
4. The named monitored source is absent, and this task performed no cloud
   authentication, external call, extension installation, resource creation,
   training run or failure injection.

The useful application is an optional developer environment for an already
approved server-side ML workload. Android should remain a status, artifact and
approval surface; it must not store Google credentials, create infrastructure
or control training workers directly.

## Ten GCP setup concerns

### 1. Evidence and workload ownership

Name one concrete Soll ML development workload, its owner, expected artifact
and current local/server baseline. Do not provision cloud resources merely to
reproduce the article anecdote. Define what measurable improvement would
justify the additional cloud surface: setup time, time to first successful
step, recovery-point loss, time to resume, developer wait time, or cost per
successful run.

### 2. Project, billing, APIs and quota

Use a dedicated non-production GCP project with an approved billing owner,
budget and resource labels. Confirm the currently required Workbench, Compute,
storage and TPU services, organization policies, region/zone availability,
accelerator quota and reservation/capacity before setup. API enablement, quota
requests and billing attachment are side effects and require operator approval.

### 3. Identity and least-privilege IAM

Use named workforce identity for the developer and a dedicated service account
for the workload. Separate permissions to connect, submit work, read the
approved bucket, write checkpoints and inspect logs. Avoid broad Owner/Editor
access, service-account impersonation beyond the selected worker and long-lived
downloaded service-account key files. Credentials and OAuth tokens must never
enter this repository, settings sync, notebooks, terminal history or artifacts.

### 4. VS Code connection and extension trust

Verify the official publisher, pinned extension/version policy and supported
Workbench connection path. Decide where OAuth/device authorization occurs and
which local folders, terminals, ports and settings the remote session can
access. Disable automatic upload or workspace sync for unrelated repositories,
secrets and personal files. A compromised extension or remote host inherits
developer-context risk even when the training data is synthetic.

### 5. Network exposure and egress

Prefer a private, authenticated connection path over a public notebook or SSH
endpoint. Review firewall rules, IAP or organization-approved tunneling,
Private Google Access/NAT needs, DNS, package-registry access and outbound
allowlists. Record internet egress, cross-region storage traffic and dependency
download costs. Do not open inbound access to make the editor connection easier.

### 6. Data, storage and privacy

Start with synthetic, non-sensitive fixtures. Place source data and checkpoints
in approved, access-controlled storage in the chosen region; define retention,
versioning, encryption/key ownership, deletion and audit behavior. Do not rely
on an instance's local or ephemeral disk for recovery. Exclude production chat,
task, Telegram, customer and credential data unless a later data-owner review
explicitly authorizes a bounded dataset.

### 7. Checkpoint and elasticity semantics

Pin the framework and verify that the exact TPU topology supports the intended
training mode. Define atomic checkpoint writes, checkpoint interval, optimizer
and dataloader state, random seeds, dataset position, restore selection and
corrupt/partial-checkpoint handling. The orchestrator must detect worker loss,
recreate a compatible topology and resume exactly once. A successful reconnect
to Workbench or VS Code is not proof that training state recovered correctly.

### 8. Reproducible development environment

Pin the Workbench image, OS/runtime, Python, framework, TPU libraries, package
lock, startup steps, VS Code extension and model/data revisions. Store only
non-secret configuration and checksums in version control. The same manifest
must build a disposable replacement, and rollback must select the prior known
good image without modifying the Android application.

### 9. Cost and resource lifecycle

Declare limits for Workbench compute, TPU time, persistent disks, snapshots,
object storage, logs and network traffic. Add budget alerts, idle shutdown,
maximum job duration, labels and a named teardown owner. Stopping a notebook UI
does not necessarily delete its disk, TPU, reservation or stored artifacts;
cleanup must enumerate and verify every billed resource.

### 10. Observability, audit and failure handling

Capture training step/epoch, checkpoint id and age, restart count, resume
reason, lost work, time to resume, job outcome, accelerator utilization and
estimated cost. Keep prompt/data bodies, tokens and credentials out of logs.
Retain identity and infrastructure audit events according to policy. Define a
stop condition for repeated restarts, corrupt checkpoints, unexpected egress,
quota exhaustion and budget breach.

## Approval-gated pilot contract

Run no pilot until all seven gates are met:

1. **Owned use case.** A workload owner provides a synthetic fixture, fixed
   baseline, expected model artifact and predeclared value threshold.
2. **Approved cloud boundary.** The operator approves the dedicated project,
   billing cap, region, APIs, quota, identity, network and retention policy.
3. **Reproducible environment.** Exact Workbench image, framework/TPU stack,
   extension version, package lock and immutable model/data revisions are
   recorded without secrets.
4. **Checkpoint correctness.** A normal run proves atomic save/restore of model,
   optimizer, input position and random state before any failure test.
5. **Bounded recovery trial.** One explicitly approved non-production
   interruption is injected into a small job; uncontrolled termination or
   repeated failure injection is forbidden.
6. **Measured comparison.** Record attach/setup time, checkpoint age/bytes,
   lost steps, recovery detection time, time to resumed progress, final-output
   equivalence, errors and total cost against the named baseline.
7. **Cleanup and decision.** Verify job, TPU/VM, Workbench runtime, disks,
   buckets/objects, reservations and temporary IAM grants are stopped, deleted
   or retained intentionally; reject adoption when the threshold is not met.

An acceptable smoke must show a successful deterministic checkpoint restore
and final-output tolerance, not merely that the editor reconnected. A result is
valid only for the tested framework, topology, image, storage and failure mode;
it cannot establish a general "recovers in seconds" claim.

## Value decision

This task adds one durable dev-tooling note, audits four current Soll seams,
documents ten GCP setup-concern categories and defines seven measurable pilot
gates. It creates no GCP or Android integration. Cloud training runs, controlled
TPU interruptions and measured runtime improvement completed by this task are
all **0**. The observed value is a reviewable setup and measurement boundary
that prevents an anecdotal recovery claim from becoming an unbounded cloud
change.
