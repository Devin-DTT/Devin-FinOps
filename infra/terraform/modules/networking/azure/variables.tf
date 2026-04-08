variable "environment_name" {
  description = "Name of the environment (used for resource naming)"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "westeurope"
}

variable "resource_group_name" {
  description = "Name of the Azure resource group"
  type        = string
}
