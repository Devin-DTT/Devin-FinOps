{{- define "finops-common.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .name }}
  labels:
    {{- include "finops-common.labels" . | nindent 4 }}
spec:
  replicas: {{ .values.replicaCount | default 1 }}
  selector:
    matchLabels:
      {{- include "finops-common.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "finops-common.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: {{ .name }}
          image: "{{ .values.image.repository }}:{{ .values.image.tag }}"
          imagePullPolicy: {{ .values.image.pullPolicy | default "IfNotPresent" }}
          ports:
            - containerPort: {{ .values.containerPort }}
          {{- if .values.env }}
          env:
            {{- toYaml .values.env | nindent 12 }}
          {{- end }}
          {{- if .values.envFrom }}
          envFrom:
            {{- toYaml .values.envFrom | nindent 12 }}
          {{- end }}
          resources:
            {{- toYaml .values.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: {{ .values.healthCheck.path | default "/actuator/health" }}
              port: {{ .values.containerPort }}
            initialDelaySeconds: 15
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: {{ .values.healthCheck.path | default "/actuator/health" }}
              port: {{ .values.containerPort }}
            initialDelaySeconds: 10
            periodSeconds: 10
          {{- if .values.volumeMounts }}
          volumeMounts:
            {{- toYaml .values.volumeMounts | nindent 12 }}
          {{- end }}
      {{- if .values.volumes }}
      volumes:
        {{- toYaml .values.volumes | nindent 8 }}
      {{- end }}
{{- end -}}
