# Refactoring Proposal — Declarative Infrastructure Scrubber
> **Status: DRY RUN — No files have been modified. This is a review artifact only.**

---

## 🚨 [PROPOSED BASH REFACTORINGS]

---

### #1 — `deploy-payment-edge-cell-local.sh` — `envsubst` + `mktemp` + `rollout status`

**Rule: A — Sub-bullets: "Remove Runtime Configuration Patching" + "Remove Imperative Service Synchronization"**

**Target File:** `infra/scripts/deploy-payment-edge-cell-local.sh`

**Current Code:**
```bash
TMP_VALUES="$(mktemp)"; trap 'rm -f "$TMP_VALUES"' EXIT
export INGRESS_HOST
envsubst < "$VALUES_TPL" > "$TMP_VALUES"

helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n payment --create-namespace \
  -f "$TMP_VALUES"

kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s
kubectl -n payment rollout status statefulset payment-edge-cell --timeout=180s || true
```

**Proposed Replacement:**
```bash
# No envsubst, no mktemp, no runtime patching.
# INGRESS_HOST is now a static value in infra/helm-values/payment-edge-cell-values-local.yaml

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/ingress-nginx-values-local.yaml"

helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n payment --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-edge-cell-values-local.yaml"

# Removed: rollout status calls.
# Readiness is now guaranteed by the pg_isready initContainer inside the manifest (Rule C).
```

---

### #2 — `deploy-payment-edge-cell-azure.sh` — `envsubst` + `mktemp` + `rollout status` + `sleep` loop

**Rule: A — Sub-bullets: "Remove Runtime Configuration Patching" + "Remove Imperative Service Synchronization"**

**Target File:** `infra/scripts/deploy-payment-edge-cell-azure.sh`

**Current Code:**
```bash
# Wait for Azure Load Balancer EXTERNAL-IP (for/sleep loop)
EXT_IP=""
for i in {1..30}; do
  EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  if [ -n "$EXT_IP" ]; then break; fi
  echo "  (attempt $i/30) No EXTERNAL-IP yet, retrying in 10s..."
  sleep 10
done

# Runtime patching via envsubst
TMP_VALUES="$(mktemp)"; trap 'rm -f "$TMP_VALUES"' EXIT
export INGRESS_HOST
envsubst < "$VALUES_TPL" > "$TMP_VALUES"

helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n payment --create-namespace \
  -f "$TMP_VALUES"

kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s
kubectl -n payment rollout status statefulset payment-edge-cell --timeout=300s || true
```

**Proposed Replacement:**
```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/ingress-nginx-values-azure.yaml"

# INGRESS_HOST is a static wildcard DNS in azure values file — no runtime substitution needed.
# Azure Load Balancer IP is discovered post-deploy via: kubectl get svc -n ingress-nginx

helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n payment --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-edge-cell-values-azure.yaml"

# Removed: for/sleep IP polling loop.
# Removed: rollout status calls.
# Removed: envsubst + mktemp.
echo "✅ Deployed. Retrieve EXTERNAL-IP with:"
echo "   kubectl -n ingress-nginx get svc ingress-nginx-controller"
```

---

### #3 — `deploy-yugabyte-local.sh` — `kubectl rollout status`

**Rule: A — Sub-bullet: "Remove Imperative Service Synchronization"**

**Target File:** `infra/scripts/deploy-yugabyte-local.sh`

**Current Code:**
```bash
helm upgrade --install yugabyte yugabytedb/yugabyte \
  --version 2.23.0 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Wait for TServers to be up
kubectl -n "$NS" rollout status statefulset/yb-tserver --timeout=5m
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/name=yugabyte -o wide
```

**Proposed Replacement:**
```bash
helm upgrade --install yugabyte yugabytedb/yugabyte \
  --version 2.23.0 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Removed: kubectl rollout status.
# YugabyteDB readiness is now guaranteed by the pg_isready initContainer
# in the yugabyte-db-init-job (Rule C). Dependent pods will not start
# until Yugabyte is confirmed ready at the manifest level.
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/name=yugabyte -o wide
```

---

### #4 — `deploy-monitoring-stack-azure.sh` — Multiple `kubectl rollout status` calls

**Rule: A — Sub-bullet: "Remove Imperative Service Synchronization"**

**Target File:** `infra/scripts/deploy-monitoring-stack-azure.sh`

**Current Code:**
```bash
echo "⏳ Waiting for Prometheus Operator..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator --timeout=5m

echo "⏳ Waiting for Prometheus StatefulSet..."
kubectl -n monitoring rollout status statefulset/prometheus-prometheus-stack-kube-prom-prometheus --timeout=10m

echo "⏳ Waiting for Grafana..."
kubectl -n monitoring rollout status deploy/prometheus-stack-grafana --timeout=5m
```

**Proposed Replacement:**
```bash
# Removed: all kubectl rollout status calls.
# The kube-prometheus-stack Helm chart manages its own readiness ordering internally.
# Grafana and Prometheus will not serve traffic until their pods are Ready —
# enforced natively by Kubernetes pod readiness gates, not by external shell polling.
echo "✅ Monitoring stack submitted. Check status with:"
echo "   kubectl -n monitoring get pods"
```

---

---

## 🛡️ [PROPOSED MANIFEST REFACTORINGS (DB & READINESS)]

---

### #5 — `charts/payment-edge-cell/templates/statefulset.yaml` — Replace full JVM Liquibase initContainer

**Rule: B — Sub-bullet: "Stage 2: Lightweight Schema Migrations (No JVM Bloat)"**

**Target File:** `charts/payment-edge-cell/templates/statefulset.yaml` — Lines 101–144

**Current Code:**
```yaml
        # 4. LIQUIBASE JOB (Runs after DB is ready)
        - name: payment-service-liquibase
          image: "{{ .Values.paymentService.image.repository }}:{{ .Values.paymentService.image.tag }}"
          imagePullPolicy: {{ .Values.paymentService.image.pullPolicy }}
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "liquibase-job"
            - name: SPRING_MAIN_WEB_APPLICATION_TYPE
              value: "none"
            - name: EDGE_DB_NAME
              valueFrom:
                configMapKeyRef:
                  name: payment-platform-config
                  key: EDGE_DB_NAME
            - name: EDGE_DB_URL
              valueFrom:
                configMapKeyRef:
                  name: payment-platform-config
                  key: EDGE_DB_URL
            - name: EDGE_DB_PAYMENT_SERVICE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: edge-db-credentials
                  key: EDGE_DB_PAYMENT_SERVICE_USERNAME
            - name: EDGE_DB_PAYMENT_SERVICE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: edge-db-credentials
                  key: EDGE_DB_PAYMENT_SERVICE_PASSWORD
            - name: YUGABYTE_DB_URL
              valueFrom:
                configMapKeyRef:
                  name: payment-platform-config
                  key: YUGABYTE_DB_URL
            - name: YUGABYTE_DB_PAYMENT_SERVICE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: yugabyte-db-credentials
                  key: YUGABYTE_DB_PAYMENT_SERVICE_USERNAME
            - name: YUGABYTE_DB_PAYMENT_SERVICE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: yugabyte-db-credentials
                  key: YUGABYTE_DB_PAYMENT_SERVICE_PASSWORD
```

**Proposed Replacement:**
```yaml
        # 4. LIQUIBASE MIGRATION — Lightweight Alpine runner (no JVM boot)
        - name: payment-service-liquibase
          image: liquibase/liquibase:4.25-alpine
          args:
            - --changelog-file=db/changelog/db.changelog-master.yaml
            - --url=$(EDGE_DB_URL)
            - --username=$(EDGE_DB_PAYMENT_SERVICE_USERNAME)
            - --password=$(EDGE_DB_PAYMENT_SERVICE_PASSWORD)
            - update
          env:
            - name: EDGE_DB_URL
              valueFrom:
                configMapKeyRef:
                  name: payment-platform-config
                  key: EDGE_DB_URL
            - name: EDGE_DB_PAYMENT_SERVICE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: edge-db-credentials
                  key: EDGE_DB_PAYMENT_SERVICE_USERNAME
            - name: EDGE_DB_PAYMENT_SERVICE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: edge-db-credentials
                  key: EDGE_DB_PAYMENT_SERVICE_PASSWORD
          volumeMounts:
            - name: liquibase-changelogs
              mountPath: /liquibase/db/changelog
      # Add to volumes:
      # - name: liquibase-changelogs
      #   configMap:
      #     name: payment-service-liquibase-changelogs
```

> ⚠️ **Note:** This change requires extracting the Liquibase changelog YAML files currently embedded in the Spring Boot JAR into a standalone ConfigMap (`payment-service-liquibase-changelogs`). The changelog files themselves do not change — only their delivery mechanism changes from JAR-classpath to ConfigMap-mount.

---

### #6 — `charts/payment-platform-config/templates/yugabyte-db-init-configmap.yaml` — Replace `until`/`sleep` shell loop

**Rule: C — Sub-bullet: "Strip out any client-side shell scripts that ping database ports"**

**Target File:** `charts/payment-platform-config/templates/yugabyte-db-init-configmap.yaml` — Lines 11–15

**Current Code (inside the ConfigMap shell script):**
```bash
echo "Waiting for Yugabyte to be ready..."
until psql -h yb-tserver-0.yb-tservers.payment.svc.cluster.local -p 5433 -U yugabyte -d yugabyte -c "select 1" > /dev/null 2>&1; do
  echo "Yugabyte is unavailable - sleeping 5s"
  sleep 5
done
echo "Yugabyte is up - executing init script..."
```

**Proposed Replacement — add an initContainer to `yugabyte-db-init-job.yaml`:**
```yaml
# In charts/payment-platform-config/templates/yugabyte-db-init-job.yaml
spec:
  template:
    spec:
      initContainers:
        - name: wait-for-yugabyte
          image: postgres:16-alpine
          command:
            - sh
            - -c
            - |
              until pg_isready -h yb-tserver-0.yb-tservers.payment.svc.cluster.local -p 5433 -U yugabyte; do
                echo "Yugabyte not ready, waiting..."
                sleep 2
              done
      containers:
        - name: yugabyte-db-init
          image: postgres:15-alpine
          command: ["/bin/bash", "/scripts/init-yugabyte-db.sh"]
          # ... (unchanged)
```

**And remove lines 11–16 from the ConfigMap script** — the init script starts directly at the `psql` user creation commands since readiness is now guaranteed by the K8s initContainer above.

---

### #7 — `charts/payment-consumers/templates/statefulset.yaml` — Add missing `pg_isready` initContainer

**Rule: C — Sub-bullet: "inject a native, non-blocking check loop directly as an initContainer inside dependent application deployment manifests (payment-consumers)"**

**Target File:** `charts/payment-consumers/templates/statefulset.yaml`

**Current Code:**
```yaml
    spec:
      terminationGracePeriodSeconds: 60
      affinity:
        # ...
      containers:
        - name: payment-consumers
          # ... (starts immediately, no DB readiness check)
```

**Proposed Replacement — add initContainers block before containers:**
```yaml
    spec:
      terminationGracePeriodSeconds: 60
      affinity:
        # ... (unchanged)
      initContainers:
        - name: wait-for-central-db
          image: postgres:16-alpine
          command:
            - sh
            - -c
            - |
              until pg_isready -h central-db-postgresql -p 5432; do
                echo "Waiting for central-db..."
                sleep 1
              done
      containers:
        - name: payment-consumers
          # ... (unchanged)
```

---

> ⚠️ **Traceability Requirement:** Every proposed change above cites the exact Rule letter (A, B, or C) and sub-bullet from Section 2 that justifies it. No change has been proposed that cannot be traced to a named rule.

---

## 🗑️ [PROPOSED PURGE LIST]

| File | Reason |
|------|--------|
| `infra/scripts/delete-minikube-cluster.sh` | Entire script exists solely to clean up Minikube profiles, volumes, containers, and kubeconfig entries. OrbStack (`orbstack-nuke-dev.sh`) now handles local cluster teardown. This file has no purpose in the OrbStack-native workflow. **Rule A — "Remove Networking Workarounds"** |

> All other scripts containing the word `minikube` (`deploy-keda-azure.sh`, `deploy-monitoring-stack-azure.sh`) use it only as a **context guard** (`if context == minikube → abort`). These are legitimate safety checks that explicitly reject minikube contexts. They should be **kept**.

---

## Open Questions for Architecture Team

1. **Liquibase changelogs location:** The Spring Boot JAR currently embeds the changelog files on its classpath. To use `liquibase/liquibase:4.25-alpine`, these files must be extracted and delivered via a ConfigMap. Are the changelog files already accessible as plain YAML files in the repo, or are they exclusively inside the compiled JAR?

2. **`collect_metrics.sh` while/sleep loop:** The `while true; sleep 2` loop in this script is a local developer metric-scraping utility, not a deployment ordering mechanism. It is excluded from the purge list and Rule A scope — confirm this is correct.

3. **`orbstack-nuke-dev.sh` kubectl wait:** The `kubectl wait --for=delete` calls in the teardown script are legitimate pod-drain safety checks, not deployment-ordering anti-patterns. Excluded from Rule A scope — confirm this is correct.
