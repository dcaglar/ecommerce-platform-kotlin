output "cluster_name" {
  value = google_container_cluster.primary.name
}

output "node_pool_name" {
  value = google_container_node_pool.primary_nodes.name
}

output "project_id" {
  value = var.project_id
}

output "region" {
  value = var.region
}

output "repository_id" {
  value = google_artifact_registry_repository.docker.repository_id
}

output "zone" {
  value = var.zone
}
