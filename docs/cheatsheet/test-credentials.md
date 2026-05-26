
If you need credentials for 'admin' user grafana dashboard then;

```
 kubectl get secret --namespace monitoring prometheus-stack-grafana -o jsonpath="{.data.admin-password}" | base64 --decode ; echo
```