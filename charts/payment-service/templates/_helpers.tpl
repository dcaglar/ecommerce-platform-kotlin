{{- define "payment-service.fullname" -}}
{{ .Release.Name }}
{{- end }}

{{- define "payment-service.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}