variable "resource_group_name" {
  type        = string
  description = "The name of the resource group."
}

variable "location" {
  type        = string
  description = "The Azure Region to deploy into."
}

variable "cluster_name" {
  type        = string
  description = "The name of the AKS cluster."
}

variable "dns_prefix" {
  type        = string
  description = "The DNS prefix for the AKS cluster."
}

variable "system_node_size" {
  type        = string
  description = "VM size for the system node pool."
}

variable "system_node_count" {
  type        = number
  description = "Number of nodes in the system node pool."
}

variable "central_node_size" {
  type        = string
  description = "VM size for the central node pool."
}

variable "central_node_count" {
  type        = number
  description = "Number of nodes in the central node pool."
}

variable "edge_node_size" {
  type        = string
  description = "VM size for the edge node pool."
}

variable "edge_node_count" {
  type        = number
  description = "Number of nodes in the edge node pool."
}

variable "edge2_node_size" {
  type        = string
  description = "VM size for the autoscaling edge node pool."
}

variable "edge2_min_count" {
  type        = number
  description = "Minimum number of nodes in the autoscaling edge node pool."
}

variable "edge2_max_count" {
  type        = number
  description = "Maximum number of nodes in the autoscaling edge node pool."
}
