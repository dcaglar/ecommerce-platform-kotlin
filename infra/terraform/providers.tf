terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = ">= 3.116.0"
    }
  }
  
  # We use local backend for simplicity right now. In a real team environment, this would be backed by Azure Storage.
  backend "local" {}
}

provider "azurerm" {
  features {}
}
