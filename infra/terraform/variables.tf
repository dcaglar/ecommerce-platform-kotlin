variable "project_id" {
  description = "GCP project ID"
  type        = string
  default     = "ecommerce-platform-dev"
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "europe-west4"
}

variable "zone" {
  description = "GCP zone"
  type        = string
  default     = "europe-west4-a"
}

variable "gke_num_nodes" {
  description = "Number of nodes in the node pool"
  type        = number
  default     = 1
}