---
task_id: 7e67fcf32f744b0d82fc777876284397
source_ref: insight/63b2d2897866
source_item: habr-sber-service-mesh-database-scaling-a4a26796
source_processing_result: service_mesh_review_completed_android_boundary_confirmed
verification_artifact: Soll/outputs/source-processing/task-7e67fcf32f744b0d82fc777876284397-service-mesh-security-audit.md
value_metric: "3 security areas audited; 0 Istio manifests, 0 application mTLS configurations, and 0 remote database drivers found; 1 release transport control confirmed; 2 follow-up boundaries recorded; 0 production changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# Service Mesh, egress and database security audit for `soll_app`

## Outcome

The full article review and repository mapping are complete. The article's
PostgreSQL pattern belongs at the Soll server/infrastructure boundary, not in
this Android client: the worktree contains no Kubernetes/Istio resources, no
application client-certificate configuration and no remote database driver.
The app reaches the Soll backend through Retrofit/OkHttp and uses Room only as
an in-process SQLite store.

No Service Mesh configuration, certificate, database connection, dependency,
Android behavior or public contract was changed. Adopting the article's design
inside this repository would put infrastructure policy and database identity
in the wrong trust boundary.

## Source reviewed

- Monitored reference:
  `monitored/habr-sber-company/20260703-211410-service-mesh-e-a4a26796.md`.
- Canonical article:
  `https://habr.com/ru/companies/sberbank/articles/1046634/`, published
  2026-07-01 and reviewed read-only on 2026-07-22.
- The monitored file is absent from this isolated worktree, so no claim below
  depends on an unseen local summary.

The article demonstrates a multi-identity PostgreSQL connection through an
Istio Egress Gateway. `ServiceEntry` registers the external database,
`DestinationRule` applies `MUTUAL` TLS with a CA, client certificate and private
key, and `VirtualService` routes the TCP stream. Its first attempt selects a
route from `sourceLabels` at the sidecar, but the source context is lost when
the gateway creates the next TCP segment. Both callers therefore receive the
same client-certificate policy and one database login fails.

The working example encodes caller identity in separate gateway ports (`5001`
and `5002`). This lets the gateway choose distinct certificate policies and the
article verifies both sessions with PostgreSQL TLS state and gateway route
logs. The trade-off is linear growth in ports, listeners, clusters and routing
configuration, so the pattern is bounded rather than a general scaling
solution.

## mTLS configuration review

Status: **review complete; no application mTLS configuration is present**.

- `AppModule.provideOkHttpClient()` builds a normal OkHttp client with logging
  redaction and timeouts. It does not install a client key manager, client
  certificate, custom trust manager or certificate pin set.
- Focused scans found no `PeerAuthentication`, mesh `DestinationRule`,
  `AuthorizationPolicy`, Istio API resource or certificate mount in the
  worktree.
- Release builds set `usesCleartextTraffic=false`; debug builds deliberately
  allow cleartext for local development and device provisioning. This is a TLS
  transport baseline, not mTLS identity.

Conclusion: there is no app-side mTLS configuration to repair or validate.
Internal service-to-service mTLS and Egress Gateway client certificates must be
owned and tested by the server deployment repository. Client private keys for
the database or mesh must never be shipped in the Android app.

## Egress policy implementation review

Status: **implementation status verified; no Service Mesh or host allowlist
policy is implemented in this worktree**.

- There is no `ServiceEntry`, Egress Gateway, `VirtualService`, Kubernetes
  `NetworkPolicy` or equivalent deployment manifest.
- `SollRepository` accepts the configured server URL. If its scheme is omitted,
  `normalizeSollBaseUrl()` prefixes `http://`; Android blocks that path in a
  release build, while the debug build can use it.
- The shared OkHttp client does not restrict requests to an allowlist. Local
  gadget provisioning intentionally creates HTTP requests to a user-selected
  LAN host, so a blanket Android host allowlist would break an existing local
  workflow and would still not replace server egress policy.

Conclusion: release cleartext denial is confirmed, but it must not be reported
as default-deny egress. If the Soll server adds an external PostgreSQL path, its
deployment must prove a default-deny network boundary, an explicit
`ServiceEntry`/gateway route and denial of an undeclared destination. No such
server-side proof can be produced from this Android-only worktree.

## Database connection security review

Status: **connection boundary confirmed; the app has no direct remote database
connection**.

- Focused dependency and source scans found no PostgreSQL, JDBC, R2DBC, MySQL,
  SQL Server, MongoDB or Redis client in production Android code.
- Remote Soll data is accessed through `SollApiService` over Retrofit/OkHttp.
  The recommended endpoint uses HTTPS, release cleartext is denied and Android
  performs the platform's normal server-certificate validation.
- `SollDatabase` is a local Room/SQLite database opened in process; a network
  TLS handshake does not apply. Explicit migrations are registered and backup
  and device-transfer rules exclude the database.
- Data at rest is a separate security gap: the Room database is not encrypted,
  while `BotConfigEntity` includes a bot token column. This review did not read
  any token value. Moving or encrypting that field needs a separately approved
  migration with recovery and device tests; it is not safe to hide inside a
  Service Mesh documentation task.

Conclusion: the Android client does not bypass the API to reach a database, so
the application connection boundary is secure by separation. The security of
the Soll server's own database connection remains an infrastructure acceptance
gate and is not claimed by this audit.

## Server-side promotion gates

Before applying the article pattern to a real Soll database workload, the
server/infrastructure owner must provide a non-production artifact that proves:

1. mesh-internal mTLS is strict and workload identity is authorized, not only
   encrypted;
2. external database egress is default-deny and only the declared host and port
   are reachable through the gateway;
3. the gateway validates the database CA and selects the expected client
   certificate without exposing private keys to an application container;
4. each database identity is least-privileged and a wrong identity is rejected;
5. `pg_stat_ssl` (or the target database equivalent) and gateway telemetry show
   the expected TLS session and policy for every caller;
6. port-per-identity growth is load-tested and capped, with an alternative
   selected before listener, cluster or policy counts become operationally
   unsafe.

## Focused smoke/audit checks

| Check | Expected | Observed result |
| --- | --- | --- |
| Full source review | failure, correction and scaling boundary captured | PASS |
| mTLS review | application and mesh configuration status explicit | PASS: none present |
| Egress review | release control separated from default-deny policy | PASS: release TLS baseline exists; allowlist/mesh policy absent |
| Remote DB boundary | no Android database driver or direct DB connection | PASS |
| Local Room boundary | migration/backup controls and at-rest gap explicit | PASS |
| Safe scope | no runtime, certificate, credential or integration changes | PASS |
| Value metric | quantified observed result attached | PASS |

`HabrSberServiceMeshSecurityAuditTest` guards the article mapping, all three
acceptance areas, the repository findings, the promotion gates and the value
metric fields in this artifact.

## Value metric update

- `source_processing_result`:
  `service_mesh_review_completed_android_boundary_confirmed`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-7e67fcf32f744b0d82fc777876284397-service-mesh-security-audit.md`
- `value_metric`: `3` security areas audited; `0` Istio manifests, `0`
  application mTLS configurations and `0` remote database drivers found; `1`
  existing release transport control confirmed; `2` follow-up boundaries
  recorded (server mesh/database proof and local Room data-at-rest handling);
  `6` server promotion gates defined; `0` production changes.
