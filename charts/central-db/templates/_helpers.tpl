{{- define "central-db.fullname" -}}
{{ .Release.Name }}
{{- end }}

{{- define "central-db.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
