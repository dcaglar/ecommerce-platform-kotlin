resource "azurerm_resource_group" "rg" {
  name     = var.resource_group_name
  location = var.location
}

# The actual Managed Kubernetes Cluster (AKS)
resource "azurerm_kubernetes_cluster" "aks" {
  name                = var.cluster_name
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  dns_prefix          = "payloadtest"

  # System Node Pool (Standard_D2s_v5)
  default_node_pool {
    name       = "systempool"
    node_count = 1
    vm_size    = "Standard_D2s_v5"
    
    # We only want AKS system pods (CoreDNS) to run here
    only_critical_addons_enabled = true
  }

  identity {
    type = "SystemAssigned"
  }
  
  network_profile {
    network_plugin    = "kubenet"
    load_balancer_sku = "standard"
  }
}

# Central Node Pool (Kafka, DBs, Central Apps)
resource "azurerm_kubernetes_cluster_node_pool" "central" {
  name                  = "centralpool"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size               = "Standard_D8s_v5"
  node_count            = 1
  
  node_labels = {
    "pool" = "central"
  }
}

# Edge Node Pool (payment-edge-cell)
resource "azurerm_kubernetes_cluster_node_pool" "edge" {
  name                  = "edgepool"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  vm_size               = "Standard_D8s_v5"
  
  # Enable Cluster Autoscaler!
  auto_scaling_enabled  = true
  min_count             = 1
  max_count             = 3
  
  node_labels = {
    "pool" = "edge"
  }
}
