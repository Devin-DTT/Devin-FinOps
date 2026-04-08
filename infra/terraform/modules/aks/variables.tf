variable "cluster_name" {
  description = "Name of the AKS cluster"
  type        = string
}

variable "location" {
  description = "Azure region for the resources"
  type        = string
  default     = "westeurope"
}

variable "resource_group_name" {
  description = "Name of the Azure resource group"
  type        = string
}

variable "node_vm_size" {
  description = "VM size for the AKS node pool"
  type        = string
  default     = "Standard_D2s_v3"
}

variable "node_min_size" {
  description = "Minimum number of nodes in the node pool"
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum number of nodes in the node pool"
  type        = number
  default     = 5
}

variable "node_desired_size" {
  description = "Desired number of nodes in the node pool"
  type        = number
  default     = 3
}

variable "acr_name" {
  description = "Name of the Azure Container Registry (must be globally unique, alphanumeric only)"
  type        = string
}

variable "subnet_id" {
  description = "ID of the subnet for the AKS node pool"
  type        = string
}
