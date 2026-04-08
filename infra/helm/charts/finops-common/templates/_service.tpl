{{- define "finops-common.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ .name }}
  labels:
    {{- include "finops-common.labels" . | nindent 4 }}
spec:
  type: {{ .values.service.type | default "ClusterIP" }}
  ports:
    - port: {{ .values.containerPort }}
      targetPort: {{ .values.containerPort }}
      protocol: TCP
      name: http
  selector:
    {{- include "finops-common.selectorLabels" . | nindent 4 }}
{{- end -}}
