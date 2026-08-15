{{/*
Common labels applied to every object this chart creates. Namespace
itself is NOT set here or anywhere in this chart — it comes from the
Argo CD Application's spec.destination.namespace (banksphere-dev /
banksphere-test), same namespace the node bootstrap script already
created and populated secrets into (see modules/k3s). See
docs/deployment/gitops.md.
*/}}
{{- define "banksphere.labels" -}}
app.kubernetes.io/part-of: banksphere
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Full image reference for a service: <registry>/<repositoryPrefix>/<repository>:<tag>,
matching modules/ecr's actual "${project_name}/${service_name}" repo
naming. Registry may be legitimately empty (pre-first-apply, see
values-dev.yaml) — in that case the prefix/repository alone is used,
which is still a valid image reference (e.g. for a future local
kind/k3d smoke test against a non-ECR registry) rather than producing a
malformed leading "/".
*/}}
{{- define "banksphere.image" -}}
{{- if .registry -}}
{{ .registry }}/{{ .repositoryPrefix }}/{{ .repository }}:{{ .tag }}
{{- else -}}
{{ .repositoryPrefix }}/{{ .repository }}:{{ .tag }}
{{- end -}}
{{- end -}}
