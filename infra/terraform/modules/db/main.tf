module "payment_db" {
  source        = "./modules/db"
  instance_name = "payment-db"
  db_version    = "POSTGRES_13"
  region        = var.region
  tier          = "db-custom-1-3840"
  db_name       = "payment"
}

module "keycloak_db" {
  source        = "./modules/db"
  instance_name = "keycloak-db"
  db_version    = "POSTGRES_15"
  region        = var.region
  tier          = "db-custom-1-3840"
  db_name       = "keycloak"
}