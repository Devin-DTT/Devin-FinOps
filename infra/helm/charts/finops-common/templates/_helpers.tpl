{{/*
Generate a fullname for the resource.
*/}}
{{- define "finops-common.fullname" -}}
{{- if .fullnameOverride }}
{{- .fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end -}}

{{/*
Common labels for all resources.
*/}}
{{- define "finops-common.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | default "0.1.0" | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/*
Selector labels used in matchLabels and pod template labels.
*/}}
{{- define "finops-common.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
