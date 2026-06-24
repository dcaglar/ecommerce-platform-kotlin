resource_group_name = "rg-payment-platform-loadtest"
location            = "westeurope"
cluster_name        = "aks-payment-loadtest"
dns_prefix          = "payloadtest"

# System Pool
system_node_size    = "Standard_D2s_v4"
system_node_count   = 1

# Central Pool
central_node_size   = "Standard_D8ds_v4"
central_node_count  = 1

# Edge Pool 1
edge_node_size      = "Standard_D8s_v3"
edge_node_count     = 1

# Edge Pool 2 (Autoscaling)
edge2_node_size     = "Standard_E8ds_v4"
edge2_min_count     = 0
edge2_max_count     = 1
