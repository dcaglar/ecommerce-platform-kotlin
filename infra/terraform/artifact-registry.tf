resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "dcaglar1987"
  description   = "Docker repository payment-service"
  format        = "DOCKER"
}