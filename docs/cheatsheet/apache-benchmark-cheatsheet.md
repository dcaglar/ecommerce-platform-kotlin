
# ⚡ Apache Benchmark (ab) Cheatsheet
### 🔹 1. 10 requests, 1 user
```bash
ab -n 500 -c 5 -T  'application/json' -p payload.json http://localhost:8081/payments

ab -n 2000 -c 50 -T  'application/json' -p payload.json http://localhost:8081/payments

ab -n 100000 -c 50 -T 'application/json' -p payload.json http://localhost:8081/payments


```




### 🔹 1. 10 requests, 2 user
```bash
ab -n 10 -c 2  -T 'application/json' -p payload.json http://localhost:8080/payments
```

### 🔹 1. 5 requests, 1 user
```bash
ab -n 50-c 5  -T 'application/json' -p payload.json http://localhost:8081/payments
```
