variable "environment_name" {
  description = "Name of the environment (used for resource naming)"
  type        = string
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-1"
}

variable "single_nat_gateway" {
  description = "Use a single NAT Gateway (cost-saving for dev, set false for prod HA)"
  type        = bool
  default     = true
}
