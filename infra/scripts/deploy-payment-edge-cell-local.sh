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