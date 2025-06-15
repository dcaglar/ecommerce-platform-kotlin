# Kafka CLI: Connecting from Local Machine (No Docker)

This guide explains how to use Kafka CLI tools installed on your local macOS machine to connect to a Kafka broker running elsewhere (e.g., in Docker or on a remote server).

## 1. Install Kafka CLI Tools

If you haven't already, install the Kafka CLI tools using Homebrew:

```sh
brew install kafka
```

This will install commands like `kafka-topics`, `kafka-console-producer`, and `kafka-console-consumer` to your PATH.

## 2. Find the Kafka Broker Address

- If Kafka is running locally (e.g., via Docker), the broker is usually accessible at:
    - `localhost:9092` (default)
    - Or `localhost:29092` (if mapped in Docker Compose)
- If Kafka is running on a remote server, use its hostname/IP and port.

## 3. Example CLI Commands

### List Topics
```sh
kafka-topics --bootstrap-server localhost:29092 --list
```

### Create a Topic
```sh
kafka-topics --bootstrap-server localhost:29092 --create --topic my-topic --partitions 1 --replication-factor 1
```


### increase partion
```sh
kafka-topics --bootstrap-server localhost:29092 --alter --topic payment_order_created_queue --partitions 32
```

```sh
kafka-topics --bootstrap-server localhost:29092 --alter --topic payment_order_retry_request_topic --partitions 32
```

### how to inspect the exception in the dlq topic
```sh
kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic payment_order_retry_request_topic-dlt \
  --from-beginning \
  --property print.headers=true 
```

### check the descripton  of the topic
```sh
 kafka-topics --bootstrap-server localhost:29092 --describe --topic payment_order_created_queue 
 ```

```sh
 kafka-topics --bootstrap-server localhost:29092 --describe --topic payment_order_retry_request_topic 
 ```






### Produce Messages
```sh
echo "hello world" | kafka-console-producer --bootstrap-server localhost:29092 --topic my-topic
```

### Consume Messages
```sh
kafka-console-consumer --bootstrap-server localhost:29092 --topic my-topic --from-beginning
```

### List All Consumer Groups
```sh
kafka-consumer-groups --bootstrap-server localhost:29092 --list
```

### Describe a Consumer Group (shows topic, partition, current offset, lag, etc.)
```sh
kafka-consumer-groups --bootstrap-server localhost:29092 --describe --group <your-consumer-group>
```

Replace `<your-consumer-group>` with the actual group name from the previous command.

## 4. Notes
- Replace `localhost:29092` with your actual broker address if different.
- No Docker is required for these CLI tools if installed via Homebrew.
- You can use these tools to interact with any accessible Kafka broker.

---
For more advanced usage, see the [official Kafka documentation](https://kafka.apache.org/documentation/).
