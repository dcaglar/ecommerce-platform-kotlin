after you starteedd action then on commandline
''' 
az login
az account set --subscription "7ff93b69-058b-4fee-8dc3-933e9d0d1b86"
az aks get-credentials --resource-group rg-payment-platform-loadtest --name aks-payment-loadtest --overwrite-existing
'''


kubectl get events -n payment --sort-by='.lastTimestamp'