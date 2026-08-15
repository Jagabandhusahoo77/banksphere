# Azure DevOps Pipeline (Phase 10A — GitOps rework)

`azure-pipelines.yml` (repository root) implements the GitOps deploy flow:

```text
Git -> Build -> Unit tests -> Frontend tests -> Docker build ->
Security validation -> Push to ECR -> Update GitOps image tag (DEV) ->
Commit -> Smoke tests (DEV) -> Deploy frontends (DEV, S3+CloudFront) ->
Update GitOps image tag (TEST) -> Smoke tests (TEST) ->
Deploy frontends (TEST) -> Future production approval (no deploy)
```

**The single biggest change from the original design**: this pipeline never SSHes or SSM-execs into an EC2 instance to deploy the application. See `docs/deployment/gitops.md` for the full reasoning.

## Prerequisites this pipeline assumes (none created by this phase)

1. **An Azure DevOps "AWS" service connection** named `banksphere-aws` (the pipeline's `awsServiceConnection` variable) — scoped to a **smaller** IAM permission set than the original design needed: ECR push + S3 (`s3:PutObject`/`s3:ListBucket` on the two portal buckets) + CloudFront (`cloudfront:CreateInvalidation`). **No longer needs** `ssm:SendCommand`/`ec2:DescribeInstances` at all — the pipeline never touches an EC2 instance directly anymore.
2. **Write access back to this repository** for the GitOps commit steps (`UpdateGitOpsDev`/`UpdateGitOpsTest`) — the pipeline's own default OAuth token works as long as GitOps state lives in this same repo (see `gitops/README.md`); a separate PAT/service connection would be needed if `gitops/` is ever split out.
3. **`infrastructure/terraform/environments/{dev,test}` already applied at least once** — the k3s nodes, ECR repositories, S3 buckets, and CloudFront distributions this pipeline pushes to/reads outputs from must actually exist.
4. **Pipeline variables** (Library, or set per-run): `AWS_REGION`; `ALB_DNS_NAME_DEV`/`ALB_DNS_NAME_TEST` and `S3_BUCKET_CUSTOMER_PORTAL_{DEV,TEST}`/`S3_BUCKET_EMPLOYEE_PORTAL_{DEV,TEST}`/`CLOUDFRONT_DIST_ID_{APP,OPS}_{DEV,TEST}` (all from `terraform output` in the matching environment — `s3_bucket_ids`, `cloudfront_distribution_ids`); once `DOMAIN_NAME` is set, smoke tests and frontend builds prefer the real hostnames automatically.

## Design choices worth calling out

- **Image tag = git commit SHA** (`$(Build.SourceVersion)`), never `latest` — enforced a second, independent way by ECR's `IMAGE_TAG_MUTABILITY=IMMUTABLE` repositories.
- **Build once, promote via a GitOps commit — for the six backend Docker images.** Each image is built and pushed to ECR exactly once per pipeline run; `UpdateGitOpsDev` then `UpdateGitOpsTest` write the SAME `image.tag` into `values-dev.yaml` then `values-test.yaml`, in sequence, gated on DEV's smoke tests passing first. Argo CD (not this pipeline) applies each change.
- **The two frontends are a deliberate, narrower exception.** Their static `dist/` build output is built **separately inside `DeployFrontendsDev`/`DeployFrontendsTest`**, each with the real `api-<env>.<domain>` URL baked into `VITE_*_SERVICE_URL` — not built once and promoted. See `docs/deployment/frontend-hosting.md` and `docs/deployment/networking.md` for why: DEV and TEST are genuinely different API origins, and Vite has no runtime env-injection mechanism for a static site. (A separate, reference-only container image for each portal — built with empty/relative URLs, for the Helm chart's disabled-by-default in-cluster Deployment — is still built once, in the `DockerBuild` stage, same as the backend images.)
- **GitOps commit, not `kubectl apply`.** `UpdateGitOpsDev`/`UpdateGitOpsTest` use `yq` for a targeted, structure-aware edit of exactly one YAML key (`image.tag`) — not `sed`, so nothing else in `values-<env>.yaml` (comments, `TODO` placeholders, formatting) is touched — then `git commit`+`push`. Argo CD picks up the change on its own reconcile loop (default: 3 minutes) — the smoke-test stages explicitly `sleep 240` first to give that loop time to run, since the pipeline no longer has a synchronous "deploy finished" signal the way the old SSM `send-command`/`wait` pattern did.
- **Security validation now also lints/templates/validates the Helm chart.** In addition to the original `terraform fmt -check`/`terraform validate` job (still present, still credential-free via `-backend=false`), a new `ValidateHelm` job runs `helm lint` + `helm template` (against both `values-dev.yaml` and `values-test.yaml`) + `kubeconform` on the rendered output — catching a broken chart before it's ever committed to `values-<env>.yaml` and picked up by Argo CD.
- **Smoke tests call a real, side-effect-free endpoint**: `POST /api/v1/auth/otp/request` with a fake identifier — unchanged reasoning from the original design (see ADR-009). The health-check path changed from the old gateway's synthetic `/healthz` to the real `/actuator/health` (see `docs/deployment/ingress.md` for why).
- **"Future production approval" is still a real, named stage that does nothing** — unchanged; see `infrastructure/terraform/environments/prod/README.md` and `gitops/environments/prod/README.md`.

## What this pipeline does NOT do (by design, this phase)

- Deploy to PROD (see above).
- Run the two real-Postgres integration tests against a real database — unchanged, still self-skipping via `PostgresAssumptions.assumeReachable()` (see the Phase 7A engineering journal entry).
- Any Entra ID / SSO step.
- Directly apply any Kubernetes manifest to DEV/TEST — the one exception (registering the Argo CD `Application` itself) happens once, at node bootstrap, from Terraform's `modules/k3s`, never from this pipeline. See `docs/deployment/gitops.md`.
