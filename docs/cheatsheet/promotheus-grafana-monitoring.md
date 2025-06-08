curl -s http://localhost:8081/actuator/prometheus \
| grep '^kafka_consumer'



curl localhost:8081/actuator/metrics/spring.kafka.listener

curl localhost:8081/actuator/metrics/kafka.consumer.records-consumed-total