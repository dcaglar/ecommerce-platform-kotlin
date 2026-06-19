=# No envsubst, no mktemp, no runtime patching.
 # INGRESS_HOST is now a static value in infra/helm-values/payment-edge-cell-values-local.yaml

 helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
   -n ingress-nginx --create-namespace \
   -f "$REPO_ROOT/infra/helm-values/ingress-nginx-values-local.yaml"

 helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
   -n payment --create-namespace \
   -f "$REPO_ROOT/infra/helm-values/payment-edge-cell-values-local.yaml"

 # Removed: rollout status calls.
 # Readiness is now guaranteed by the pg_isready initContainer inside the manifest (Rule C).