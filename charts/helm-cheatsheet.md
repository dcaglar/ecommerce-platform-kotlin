kubectl port-forward -n payment svc/keycloak 8080:80
--this update payment service with config map

helm upgrade payment-service ./charts/payment-service -n payment

helm template payment-service ./charts/payment-service -n payment | head \
helm list -n payment                                        
helm upgrade --install payment-service ./charts/payment-service

helm list -n payment

kubectl get pods -n payment -l app=payment-service

kubectl rollout restart deployment payment-service -n payment

kubectl delete secret payment-db-credentials -n payment

helm upgrade --install payment-secrets ./charts/payment-secrets -n payment --create-namespace

kubectl delete secret payment-db-credentials -n payment --ignore-not-found

helm upgrade --install payment-secrets ./charts/payment-secrets -n payment --create-namespace

helm secrets upgrade --install payment-platform-config . \
-n payment --create-namespace \
-f values.yaml \
-f secrets.yaml

