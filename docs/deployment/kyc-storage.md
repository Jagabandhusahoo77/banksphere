# KYC Document Storage (AWS DEV/TEST, Phase 10A)

## What kyc-service actually needs

Per inspection (see the Phase 10A report), `kyc-service`'s `LocalDocumentStorage` (Phase 9C, unchanged this phase — see ADR-008) writes uploaded KYC documents to `banksphere.kyc.document-storage-path` (env var `KYC_DOCUMENT_STORAGE_PATH`, same as local dev) — a plain filesystem path, `/data/kyc-documents` by default, with UUID-generated filenames (never the customer's own filename — see `LocalDocumentStorage`'s own javadoc). It has **zero direct AWS SDK dependency** — this was a deliberate Phase 9C design choice specifically so a future S3-backed implementation is a drop-in `DocumentStorage` swap, not a rewrite.

## What this phase provisions (post-rework)

Translated to Kubernetes: a `PersistentVolumeClaim` (`kyc-service-data`, see `gitops/apps/banksphere/templates/backend-pvc.yaml`, dynamically provisioned by k3s's bundled `local-path-provisioner`), mounted at `/data/kyc-documents` inside the `kyc-service` Pod — same mount path as `docker/local/docker-compose.yml`'s own volume, so `KYC_DOCUMENT_STORAGE_PATH` needs no environment-specific value. On a single-node cluster, `local-path-provisioner` backs every PVC with a directory on the node's own root EBS volume — the same volume Postgres's PVC uses, still provisioned **encrypted** (`infrastructure/terraform/modules/ec2`'s `root_block_device.encrypted = true`) — satisfying "provide persistent encrypted storage for DEV and TEST."

**Survives a Pod restart/recreate**: yes — this is exactly what a PVC is for. A new image tag reaching `kyc-service` via the GitOps/Argo CD flow (see `docs/deployment/gitops.md`) replaces the Pod, not the PVC; the same claim is re-mounted. A document uploaded before a deploy is still there after it.

**Does NOT survive instance termination**: if the EC2 instance itself is terminated (not stopped — terminated) without the EBS volume being preserved or snapshotted first, the documents are lost with it, same caveat as `docs/deployment/postgresql.md`'s backup section (which is itself now an open gap post-rework — see that doc). No automated EBS snapshot schedule is created this phase.

**DEV and TEST do not share this volume** — two entirely separate EC2 instances/k3s nodes means two entirely separate `local-path-provisioner` directories, satisfying "TEST must not share persistent storage with DEV."

## Future PROD architecture (documented only — not built)

**Amazon S3**, once a real PROD phase is undertaken:

- Versioning enabled (protects against accidental overwrite/delete — a real concern for a compliance-relevant document).
- Server-side encryption (SSE-S3 or SSE-KMS).
- A bucket policy blocking all public access — these are never public documents.
- `kyc-service` gains a new `S3DocumentStorage implements DocumentStorage` — because `DocumentStorage` is already an interface with exactly two methods (`store`/`load`), this is genuinely a drop-in swap: no change to `KycApplicationServiceImpl`, no change to any controller, no change to the document upload/verification business logic. See ADR-008's own "future S3 migration" note, written at the time `DocumentStorage` was first designed specifically so this would be true.
- The EC2 instance role (or, in a PROD EKS world, a pod-level IAM role) would need `s3:PutObject`/`s3:GetObject` scoped to that one bucket — the same least-privilege pattern this phase already applies to ECR/SSM (see `infrastructure/terraform/modules/ec2`).

Not implemented this phase, per the task's own explicit instruction ("Do not implement S3 yet").
