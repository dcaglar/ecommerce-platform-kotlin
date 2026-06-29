and one other thing is i did have faced with  unforeseen cloud costs, can you plewase explain me the  billiung report , what caoused what, it smeed to me that in dpeloy infra whatever we do proviusion,  when we do [destroy-infra.yml](file;file:///Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin/.github/workflows/destroy-infra.yml)  , leaves some resoursces open , i am not sure cant prove that, but i can at leasr for exmaple tell you that. even thopguh what we defined in [aks-loadtest.tfvars](file;file:///Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin/infra/terraform/aks-loadtest.tfvars)

terraform output shows

# azurerm_kubernetes_cluster.aks will be created
+ resource "azurerm_kubernetes_cluster" "aks" {
    + ai_toolchain_operator_enabled       = false
    + current_kubernetes_version          = (known after apply)
    + dns_prefix                          = "payloadtest"
    + fqdn                                = (known after apply)
    + http_application_routing_zone_name  = (known after apply)
    + id                                  = (known after apply)
    + kube_admin_config                   = (sensitive value)
    + kube_admin_config_raw               = (sensitive value)
    + kube_config                         = (sensitive value)
    + kube_config_raw                     = (sensitive value)
    + kubernetes_version                  = (known after apply)
    + location                            = "westeurope"
    + name                                = "aks-payment-loadtest"
    + node_os_upgrade_channel             = "NodeImage"
    + node_resource_group                 = (known after apply)
    + node_resource_group_id              = (known after apply)
    + oidc_issuer_enabled                 = (known after apply)
    + oidc_issuer_url                     = (known after apply)
    + portal_fqdn                         = (known after apply)
    + private_cluster_enabled             = false
    + private_cluster_public_fqdn_enabled = false
    + private_dns_zone_id                 = (known after apply)
    + private_fqdn                        = (known after apply)
    + resource_group_name                 = "rg-payment-platform-loadtest"
    + role_based_access_control_enabled   = true
    + run_command_enabled                 = true
    + sku_tier                            = "Free"
    + support_plan                        = "KubernetesOfficial"
    + workload_identity_enabled           = false

    + auto_scaler_profile (known after apply)

    + bootstrap_profile (known after apply)

    + default_node_pool {
        + kubelet_disk_type            = (known after apply)
        + max_pods                     = (known after apply)
        + name                         = "systempool"
        + node_count                   = 1
        + node_labels                  = (known after apply)
        + only_critical_addons_enabled = true
        + orchestrator_version         = (known after apply)
        + os_disk_size_gb              = (known after apply)
        + os_disk_type                 = "Managed"
        + os_sku                       = (known after apply)
        + scale_down_mode              = "Delete"
        + type                         = "VirtualMachineScaleSets"
        + ultra_ssd_enabled            = false
        + vm_size                      = "Standard_D2s_v4"
        + workload_runtime             = (known after apply)
          }

    + identity {
        + principal_id = (known after apply)
        + tenant_id    = (known after apply)
        + type         = "SystemAssigned"
          }

    + kubelet_identity (known after apply)

    + network_profile {
        + dns_service_ip     = (known after apply)
        + ip_versions        = (known after apply)
        + load_balancer_sku  = "standard"
        + network_data_plane = "azure"
        + network_mode       = (known after apply)
        + network_plugin     = "kubenet"
        + network_policy     = (known after apply)
        + outbound_type      = "loadBalancer"
        + pod_cidr           = (known after apply)
        + pod_cidrs          = (known after apply)
        + service_cidr       = (known after apply)
        + service_cidrs      = (known after apply)

        + load_balancer_profile (known after apply)

        + nat_gateway_profile (known after apply)
          }

    + node_provisioning_profile (known after apply)

    + windows_profile (known after apply)
      }

# azurerm_kubernetes_cluster_node_pool.central will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "central" {
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_pods              = (known after apply)
    + mode                  = "User"
    + name                  = "centralpool"
    + node_count            = 1
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "central"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_D8ds_v4"
      }

# azurerm_kubernetes_cluster_node_pool.edge will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "edge" {
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_pods              = (known after apply)
    + mode                  = "User"
    + name                  = "edgepool"
    + node_count            = 1
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "edge"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_D8s_v3"
      }

# azurerm_kubernetes_cluster_node_pool.edge2 will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "edge2" {
    + auto_scaling_enabled  = true
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_count             = 1
    + max_pods              = (known after apply)
    + min_count             = 0
    + mode                  = "User"
    + name                  = "edgepool2"
    + node_count            = (known after apply)
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "edge"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_E8ds_v4"
      }

# azurerm_resource_group.rg will be created
+ resource "azurerm_resource_group" "rg" {
    + id       = (known after apply)
    + location = "westeurope"
    + name     = "rg-payment-platform-loadtest"
      }


and one other thing is i did have faced with  unforeseen cloud costs, can you plewase explain me the  billiung report , what caoused what, it smeed to me that in dpeloy infra whatever we do proviusion,  when we do [destroy-infra.yml](file;file:///Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin/.github/workflows/destroy-infra.yml)  , leaves some resoursces open , i am not sure cant prove that, but i can at leasr for exmaple tell you that. even thopguh what we defined in [aks-loadtest.tfvars](file;file:///Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin/infra/terraform/aks-loadtest.tfvars)

terraform output shows

# azurerm_kubernetes_cluster.aks will be created
+ resource "azurerm_kubernetes_cluster" "aks" {
    + ai_toolchain_operator_enabled       = false
    + current_kubernetes_version          = (known after apply)
    + dns_prefix                          = "payloadtest"
    + fqdn                                = (known after apply)
    + http_application_routing_zone_name  = (known after apply)
    + id                                  = (known after apply)
    + kube_admin_config                   = (sensitive value)
    + kube_admin_config_raw               = (sensitive value)
    + kube_config                         = (sensitive value)
    + kube_config_raw                     = (sensitive value)
    + kubernetes_version                  = (known after apply)
    + location                            = "westeurope"
    + name                                = "aks-payment-loadtest"
    + node_os_upgrade_channel             = "NodeImage"
    + node_resource_group                 = (known after apply)
    + node_resource_group_id              = (known after apply)
    + oidc_issuer_enabled                 = (known after apply)
    + oidc_issuer_url                     = (known after apply)
    + portal_fqdn                         = (known after apply)
    + private_cluster_enabled             = false
    + private_cluster_public_fqdn_enabled = false
    + private_dns_zone_id                 = (known after apply)
    + private_fqdn                        = (known after apply)
    + resource_group_name                 = "rg-payment-platform-loadtest"
    + role_based_access_control_enabled   = true
    + run_command_enabled                 = true
    + sku_tier                            = "Free"
    + support_plan                        = "KubernetesOfficial"
    + workload_identity_enabled           = false

    + auto_scaler_profile (known after apply)

    + bootstrap_profile (known after apply)

    + default_node_pool {
        + kubelet_disk_type            = (known after apply)
        + max_pods                     = (known after apply)
        + name                         = "systempool"
        + node_count                   = 1
        + node_labels                  = (known after apply)
        + only_critical_addons_enabled = true
        + orchestrator_version         = (known after apply)
        + os_disk_size_gb              = (known after apply)
        + os_disk_type                 = "Managed"
        + os_sku                       = (known after apply)
        + scale_down_mode              = "Delete"
        + type                         = "VirtualMachineScaleSets"
        + ultra_ssd_enabled            = false
        + vm_size                      = "Standard_D2s_v4"
        + workload_runtime             = (known after apply)
          }

    + identity {
        + principal_id = (known after apply)
        + tenant_id    = (known after apply)
        + type         = "SystemAssigned"
          }

    + kubelet_identity (known after apply)

    + network_profile {
        + dns_service_ip     = (known after apply)
        + ip_versions        = (known after apply)
        + load_balancer_sku  = "standard"
        + network_data_plane = "azure"
        + network_mode       = (known after apply)
        + network_plugin     = "kubenet"
        + network_policy     = (known after apply)
        + outbound_type      = "loadBalancer"
        + pod_cidr           = (known after apply)
        + pod_cidrs          = (known after apply)
        + service_cidr       = (known after apply)
        + service_cidrs      = (known after apply)

        + load_balancer_profile (known after apply)

        + nat_gateway_profile (known after apply)
          }

    + node_provisioning_profile (known after apply)

    + windows_profile (known after apply)
      }

# azurerm_kubernetes_cluster_node_pool.central will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "central" {
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_pods              = (known after apply)
    + mode                  = "User"
    + name                  = "centralpool"
    + node_count            = 1
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "central"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_D8ds_v4"
      }

# azurerm_kubernetes_cluster_node_pool.edge will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "edge" {
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_pods              = (known after apply)
    + mode                  = "User"
    + name                  = "edgepool"
    + node_count            = 1
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "edge"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_D8s_v3"
      }

# azurerm_kubernetes_cluster_node_pool.edge2 will be created
+ resource "azurerm_kubernetes_cluster_node_pool" "edge2" {
    + auto_scaling_enabled  = true
    + id                    = (known after apply)
    + kubelet_disk_type     = (known after apply)
    + kubernetes_cluster_id = (known after apply)
    + max_count             = 1
    + max_pods              = (known after apply)
    + min_count             = 0
    + mode                  = "User"
    + name                  = "edgepool2"
    + node_count            = (known after apply)
    + node_image_version    = (known after apply)
    + node_labels           = {
        + "pool" = "edge"
          }
    + orchestrator_version  = (known after apply)
    + os_disk_size_gb       = (known after apply)
    + os_disk_type          = "Managed"
    + os_sku                = (known after apply)
    + os_type               = "Linux"
    + priority              = "Regular"
    + scale_down_mode       = "Delete"
    + spot_max_price        = -1
    + ultra_ssd_enabled     = false
    + vm_size               = "Standard_E8ds_v4"
      }

# azurerm_resource_group.rg will be created
+ resource "azurerm_resource_group" "rg" {
    + id       = (known after apply)
    + location = "westeurope"
    + name     = "rg-payment-platform-loadtest"
      }



but also on azure porttal when i do want to see what resource group i see following resource group and most importantly look what resorces it does create withoput tetrrafom knowingg anything 


MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope
"25df94c6-51d4-43fc-926e-342c413a58dd","Public IP address","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Network/publicIPAddresses/25df94c6-51d4-43fc-926e-342c413a58dd"
"aks-agentpool-22133193-nsg","Network security group","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Network/networkSecurityGroups/aks-agentpool-22133193-nsg"
"aks-agentpool-22133193-routetable","Route table","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/mc_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Network/routeTables/aks-agentpool-22133193-routetable"
"aks-centralpool-34168518-vmss","Virtual machine scale set","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Compute/virtualMachineScaleSets/aks-centralpool-34168518-vmss"
"aks-edgepool-21547863-vmss","Virtual machine scale set","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Compute/virtualMachineScaleSets/aks-edgepool-21547863-vmss"
"aks-edgepool2-14079422-vmss","Virtual machine scale set","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Compute/virtualMachineScaleSets/aks-edgepool2-14079422-vmss"
"aks-payment-loadtest-agentpool","Managed Identity","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.ManagedIdentity/userAssignedIdentities/aks-payment-loadtest-agentpool"
"aks-systempool-38069633-vmss","Virtual machine scale set","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Compute/virtualMachineScaleSets/aks-systempool-38069633-vmss"
"aks-vnet-22133193","Virtual network","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Network/virtualNetworks/aks-vnet-22133193"
"kubernetes","Load balancer","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/MC_rg-payment-platform-loadtest_aks-payment-loadtest_westeurope/providers/Microsoft.Network/loadBalancers/kubernetes"




NetworkWatcherRG


rg-payment-platform-loadtest
NAME,TYPE,LOCATION,RESOURCE LINK
"aks-payment-loadtest","Kubernetes service","West Europe","https://portal.azure.com#resource/subscriptions/7ff93b69-058b-4fee-8dc3-933e9d0d1b86/resourceGroups/rg-payment-platform-loadtest/providers/Microsoft.ContainerService/managedClusters/aks-payment-loadtest"


rg-terraform-state

