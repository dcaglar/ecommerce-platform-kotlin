#!/bin/bash
set -euo pipefail


helm uninstall kafka -n payment --ignore-not-found


  kubectl -n payment delete statefulset kafka-controller --ignore-not-found
kubectl delete pvc -n payment data-kafka-controller-0 --ignore-not-found
  kubectl -n payment  delete secret kafka-kraft --ignore-not-found







echo "✅ Uninstalled  kafka, and deleted pvc from namespace: payment"