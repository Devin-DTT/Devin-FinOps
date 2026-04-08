# -----------------------------------------------------------------------------
# AWS Networking Module
# VPC, Subnets, IGW, NAT Gateway, Route Tables for EKS
# Mirrors the existing CloudFormation networking resources in Terraform
# -----------------------------------------------------------------------------

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.environment_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["${var.region}a", "${var.region}b"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.10.0/24", "10.0.20.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = var.single_nat_gateway
  enable_dns_hostnames = true
  enable_dns_support   = true

  # Tags required for EKS to discover subnets
  public_subnet_tags = {
    "kubernetes.io/role/elb"                                = "1"
    "kubernetes.io/cluster/${var.environment_name}-cluster" = "shared"
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"                       = "1"
    "kubernetes.io/cluster/${var.environment_name}-cluster" = "shared"
  }

  tags = {
    Environment = var.environment_name
    ManagedBy   = "terraform"
  }
}
