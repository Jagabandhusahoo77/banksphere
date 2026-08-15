# PostgreSQL (AWS DEV/TEST, Phase 10A)

## Decision: Postgres in a Docker container on the EC2 instance, not RDS

The task was explicit: "Do not provision RDS unless inspection proves it is required." Inspection (see the Phase 10A report's own repository-inspection section) found nothing that requires RDS specifically — the application already treats Postgres purely as "a reachable `DB_HOST:DB_PORT`" (every service's `application.yml` reads `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` from the environment, with no code path that assumes RDS-specific behavior). For a cost-conscious dev/test learning environment, RDS's minimum realistic cost (even a `db.t3.micro` Single-AZ instance, roughly $12-15/month, before storage) is a meaningful recurring cost for capability (automated backups, Multi-AZ failover, managed patching) that this phase's actual requirements don't need — DEV/TEST tolerate downtime and don't need automatic failover.

**This is a phase-scoped decision, not a permanent one** — see "Future production recommendation" below.

## What's actually provisioned (post-rework)

One `postgres:16-alpine` Pod per environment (see `gitops/apps/banksphere/templates/postgres-deployment.yaml`), scheduled on the same single k3s node as every other service, with:

- **Persistent, encrypted storage**: a `PersistentVolumeClaim` (`postgres-data`), dynamically provisioned by k3s's bundled `local-path-provisioner` — which, on a single-node cluster, is backed by the node's own root EBS volume, still provisioned with `encrypted = true` in `infrastructure/terraform/modules/ec2` (`root_block_device.encrypted`). Surviving a Pod restart/recreate is confirmed by this being a real PVC, not an `emptyDir`/container-local path — the Deployment uses `strategy: { type: Recreate }` specifically because this is a `ReadWriteOnce` volume (see `docs/deployment/helm.md`).
- **A separate database per environment**: DEV's Postgres and TEST's Postgres are two entirely separate Pods on two entirely separate k3s nodes/EC2 instances/EBS volumes — there is no shared database server between the environments at all.
- **All six application databases**, created the same way as local dev: `POSTGRES_DB=banksphere_customer` (the official image's own bootstrap) + a ConfigMap-mounted init script (`gitops/apps/banksphere/templates/postgres-configmap.yaml`, generated from `values.yaml`'s `postgres.additionalDatabases` list — the same content as `docker/postgres/init/001-create-databases.sh`, kept from drifting apart by being generated rather than hand-copied) creating `banksphere_account`/`banksphere_transaction`/`banksphere_beneficiary`/`banksphere_employee`/`banksphere_kyc`.
- **Flyway migrations**: unchanged — each service still runs its own `V*__*.sql` migrations against its own database on startup (`ddl-auto: validate`, never auto-generated schema), exactly as in local dev. Nothing about running in Kubernetes changes this.
- **A generated, per-environment password**: see `docs/deployment/secrets.md`.

Not published on any host port or NodePort (see `docs/deployment/networking.md`) — reachable only as a ClusterIP Kubernetes Service (`postgres`, port 5432) from other Pods in the same namespace, or via `kubectl exec` for anyone who already has cluster access (through an SSM session running `kubectl`, not a direct SSH/network path — see `docs/deployment/networking.md`).

## Backups

`infrastructure/scripts/<env>/backup-postgres.sh` (the original Docker-Compose-era script, using `docker exec`) is retained as a **reference/local-fallback artifact only** — see `infrastructure/docker/README.md`. It does **not** work unmodified against the Kubernetes-hosted Postgres this rework introduces (there is no `banksphere-postgres` Docker container anymore, only a Pod inside k3s).

**A Kubernetes-native equivalent — `kubectl exec <postgres-pod> -- pg_dumpall ...`, run over an SSM session — is the natural replacement**, functionally identical to the original script's own approach (same `pg_dumpall` choice, same gzip-and-prune-after-7-days design), but **it has not been built this phase**. This is called out explicitly as an open gap, not silently assumed carried over: the original script is Docker-Compose-specific and would need a genuine rewrite (targeting a Pod, not a container name) before it does anything useful against DEV/TEST as they exist after this rework.

**It is, and would remain, explicitly NOT an off-instance backup** even once rewritten: the same single-EBS-volume limitation applies (see the original script's own documented caveat, still accurate) — if the node's EBS volume is lost, both the live data and any backup written to that same volume are lost together.

No automated schedule is created by Terraform this phase, for the same reason as before: avoiding a false sense of "backups are handled" before the off-instance gap is actually closed, and (now) before the script itself is even rewritten to target Kubernetes.

**The natural next step** (not built this phase): either (a) have `backup-postgres.sh` additionally `aws s3 cp` the dump to a versioned S3 bucket, or (b) use AWS Backup with an EBS snapshot schedule on the volume directly. Either is a small addition once genuinely needed — left out now to avoid adding S3/AWS Backup infrastructure for a learning environment where losing DEV/TEST data has no real consequence.

## Future production recommendation

**RDS for PostgreSQL, Multi-AZ**, once a real PROD phase is undertaken (see `infrastructure/terraform/environments/prod/README.md`). At production scale/availability requirements, RDS's automated backups, point-in-time recovery, Multi-AZ automatic failover, and managed patching stop being "nice to have" and start being the actual bar for a system handling (fictional, but production-shaped) banking data — the cost that isn't justified for a DEV/TEST learning environment is squarely justified there. This is intentionally not built now, and not partially built now — RDS should be its own deliberate piece of a PROD phase, not backed into this phase's Postgres-on-EC2 design.
