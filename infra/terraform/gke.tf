resource "google_container_cluster" "primary" {
  name                     = "dev-gke-cluster"
  location                 = var.zone
  initial_node_count = 1    # <-- add this line
  remove_default_node_pool = true

  logging_service    = "none"
  monitoring_service = "none"
}

resource "google_container_node_pool" "primary_nodes" {
  name       = "dev-node-pool"
  cluster    = google_container_cluster.primary.name
  location   = var.zone
  node_count = 1

  autoscaling {
    min_node_count = 0
    max_node_count = 2
  }

  node_config {
    machine_type = "e2-standard-4"  # 4 vCPU, 16 GB RAM
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
  }
}