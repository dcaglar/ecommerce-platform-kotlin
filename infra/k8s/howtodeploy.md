--cheeatsheet
1004 ./keycloak/get-token.shkubectl apply -f kafka/statefulset.yaml\n
1005 kubectl apply -f kafka/statefulset.yaml\n
1006 kubectl apply -f kafka/statefulset.yaml\n
1007 cd ~/IdeaProjects/ecommerce-platform-kotlin/infra/k8s

kubectl logs -n payment payment-service-576dd67876-drfdw

--build paymentservice script
./scripts/build-payment-service.sh

--push paymentservice image
./scripts/push-payment-service.sh

--provision keycloak for authentication
./infra/k8s/keycloak/deploy-keycloak-provisioner.sh

--deploy payment-service
kubectl apply -f payment/payment-service-deployment.yaml

---delete payment-service
kubectl delete deployment payment-service -n payment

--delete payment-db
kubectl delete statefulset payment

--start payment-db
kubectl apply -f payment/payment-db-statefulset.yaml
kubectl apply -f payment/payment-db-service.yaml

--start keycloak-db
kubectl apply -f keycloak/keycloak-db-statefulset.yaml
kubectl apply -f keycloak/keycloak-db-service.yaml

--start zookeeper
kubectl apply -f zookeeper/zookeeper-statefulset.yaml
kubectl apply -f zookeeper/zookeeper-service.yaml

--start kafka
kubectl apply -f kafka/kafka-statefulset.yaml
kubectl apply -f kafka/kafka-service.yaml

--start redis
kubectl apply -f redis/deployment.yaml -- statefulset should be used for redis
kubectl apply -f redis/redis-service.yaml --internal network access only