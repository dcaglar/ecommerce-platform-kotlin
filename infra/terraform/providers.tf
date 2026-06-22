terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = ">= 3.116.0"
    }
  }
  
  # Remote backend using Azure Storage Account (configured via CI/CD pipelines)
  backend "azurerm" {
    resource_group_name  = "rg-terraform-state"
    storage_account_name = "tfstateloadtestdc"
    container_name       = "tfstate"
    key                  = "loadtest.terraform.tfstate"
  }
}

provider "azurerm" {
  features {}
}
