{{- define "finops-common.hpa" -}}
{{- if .values.hpa.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ .name }}
  labels:
    {{- include "finops-common.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ .name }}
  minReplicas: {{ .values.hpa.minReplicas | default 1 }}
  maxReplicas: {{ .values.hpa.maxReplicas | default 3 }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .values.hpa.targetCPUUtilization | default 70 }}
{{- end }}
{{- end -}}
