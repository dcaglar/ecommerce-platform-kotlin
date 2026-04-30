TEST_TYPE=smoke_V1_D1 k6 run -o experimental-prometheus-rw load-tests/k6-payment-flow.js   \
-e K6_PROMETHEUS_RW_SERVER_URL='http://localhost:9090/api/v1/write' \
-e K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),avg,min,max' 

TEST_TYPE=smoke_V5_D10 k6 run -o experimental-prometheus-rw load-tests/k6-payment-flow.js  \
-e K6_PROMETHEUS_RW_SERVER_URL='http://localhost:9090/api/v1/write' \
-e K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),avg,min,max' 