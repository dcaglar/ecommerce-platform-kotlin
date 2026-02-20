kubectl get secret --namespace monitoring prometheus-stack-grafana -o jsonpath="{.data.admin-user}" | base64 --decode ; echo

kubectl get secret --namespace monitoring prometheus-stack-grafana -o jsonpath="{.data.admin-password}" | base64 --decode ; echo

-- deletetimeseies wring data
curl -i -X POST \
-g 'http://localhost:9090/api/v1/admin/tsdb/delete_series?match[]={test_type="stress_arrival_rate"}'

7. Capture a screenshot of the Payment Service Dashboard.
8. Identify and record: Request Rate, P99 Latency, and Error Rate.
localhost:3000/d/asdasdasdas/payment-db-dc?var-interval=$__auto&orgId=1&from=2026-02-09T22:47:16.643Z&to=2026-02-09T23:15:07.062Z&timezone=browser&var-DS_PROMETHEUS=prometheus&var-namespace=&var-release=&var-instance=10.244.0.222:9187&var-datname=payment_db&var-mode=$__all&refresh=10s

payment-db monitoring dashboard adi : "Payment DB-DC"
payment-service monitoring dashboard adi: "Payment Dashboard"




please add your complrehensive thinking loud assesment of monitoring datas and ways append here below eveything 

