After deploying helm chart via
(notice the secrets part whicj make it also possible for helm to load edge-cell-sops-secret.yaml  in a decrypted way(help of Sops),
deployment time -> helm secrets plugin steps in. It looks at your local machine, finds your private decryption key, and decrypts the sops.secrets.yml file in memory

helm secrets upgrade --install payment-edge-cell "$CHART_ROOT" \
-n payment --create-namespace \
-f "$CHART_ROOT/values.yaml" \
-f "$CHART_ROOT/local/values.yaml" \
-f "secrets://$REPO_ROOT/edge-cell-sops-secrets.yaml"

helm template rendering  combines 3 yml speficied in the previos command.
and look under template  folder there is a file   edge-cell-secrets.yaml simply  do tthe matching and define what is needed and create a typical seccret object
helm does send this secret object via kube api to be stored in etcd.
when pod  want to inject the file theyt areqiesdt frpom api
in order to see the result run the get valuies below
helm get values payment-edge-cell -n payment -> this returns the merged Values.yml  which was merged during

also we have edge-cel-configmap which filter the elements located under Values.config, so effcieint,

very similar logic to edge-cell-sops-secret.yaml also it does apply filter .