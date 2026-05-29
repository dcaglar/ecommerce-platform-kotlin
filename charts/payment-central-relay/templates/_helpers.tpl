{{- define "payment-central-relay.fullname" -}}
{{ .Release.Name }}
{{- end }}

{{- define "payment-central-relay.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
