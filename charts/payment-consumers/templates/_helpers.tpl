{{- define "payment-consumers.fullname" -}}
{{ .Release.Name }}
{{- end }}

{{- define "payment-consumers.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
