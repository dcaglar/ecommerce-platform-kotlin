variable "resource_group_name" {
  type        = string
  description = "The name of the resource group."
  default     = "rg-payment-platform-loadtest"
}

variable "location" {
  type        = string
  description = "The Azure Region to deploy into."
  default     = "westeurope"
}

variable "cluster_name" {
  type        = string
  description = "The name of the AKS cluster."
  default     = "aks-payment-loadtest"
}
