# 📊 ELK + Filebeat + Docker Troubleshooting Cheat Sheet

---

## 🟦 1. 🔍 Elasticsearch

### 🔧 Basic Health & Info
```bash
curl http://localhost:9200/_cluster/health?pretty         # Cluster health
curl -s "http://localhost:9200/_cat/indices?v"         # List all indices
curl http://localhost:9200/_cat/nodes?v                   # Node info
```

### 🔍 Query Logs
```bash
curl -X POST http://localhost:9200/payment-service-logs*/_search -H 'Content-Type: application/json' -d '
{
  "query": {
    "match": {
      "traceId": "your-trace-id"
    }
  },
  "sort": [
    { "@timestamp": "desc" }
  ],
  "size": 50
}'
```

---

## 🟦 2. 📥 Filebeat

### 🔁Connect to filebeat container
```bash
docker compose down -v filebeat elasticsearch kibana
docker volume rm <volume_name_if_needed>
docker compose up -d filebeat elasticsearch kibana
```

### ✅ Check Filebeat Status
```bash
docker logs filebeat
```



### 🔍 Troubleshoot Ingest
- Check file paths are correct and mounted:
  ```yaml
  - /your/local/logs:/app-logs:ro
  ```
- Ensure logs are **newline-delimited** JSON and match this in `filebeat.yml`:
  ```yaml
  json.keys_under_root: true
  ```

### 🔁 Force reindex on failure
```bash
docker compose down -v filebeat elasticsearch kibana
docker volume rm <volume_name_if_needed>
docker compose up -d filebeat elasticsearch kibana
```

---

## 🟦 3. 📈 Kibana

### 🔍 Access UI
- URL: [http://localhost:5601](http://localhost:5601)

### 📁 Create Index Pattern
1. Go to **Management → Stack Management → Index Patterns**
2. Add: `payment-service-logs*`
3. Select `@timestamp` as the time field

### 🔎 Discover Logs
- Use **filters like**:
  ```
  traceId: "your-trace-id"
  eventId: "uuid-value"
  logger_name: "your.package.name"
  ```

### 🧹 Delete an Index
```bash
curl -X DELETE http://localhost:9200/payment-service-logs*
```

---

## 🐳 4. Docker + Volumes + ELK Specifics

### 🔧 Common Compose Commands
```bash
docker compose ps
docker compose logs filebeat
docker compose up -d
docker compose down
docker compose down -v
```

### 📦 Named Volume Persistence
```yaml
elasticsearch:
  volumes:
    - elastic_data:/usr/share/elasticsearch/data
```

- Delete with:
```bash
docker volume rm elastic_data
```

### 🧪 Manual log injection (for testing)
```bash
echo '{"@timestamp":"2025-06-01T00:00:00", "traceId":"abc123", "message":"Hello from test"}' >> /your/log/path/payment-service.log
```

---

## 🧠 Common Pitfalls

| Symptom | Check |
|--------|-------|
| No logs in Kibana | Is Filebeat watching the correct folder? Is it mounted read-only? |
| Kibana "no data" | Did you create the index pattern? Is Elasticsearch storing anything? |
| Logs missing fields | Is Filebeat parsing JSON correctly (`json.keys_under_root: true`)? |
| Old logs not showing | Use a wider time range in Kibana (e.g., last 1 hour/day) |
| Filebeat silent | Try `docker logs filebeat` or check filebeat.yml path |
| Elasticsearch won't start | Memory limits or volume lock issues. Try `docker system prune -af` |