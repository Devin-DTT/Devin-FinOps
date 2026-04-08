output "cluster_endpoint" {
  description = "EKS cluster API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_certificate_authority" {
  description = "Base64 encoded certificate data for the cluster"
  value       = module.eks.cluster_certificate_authority_data
}

output "ecr_repository_urls" {
  description = "Map of service name to ECR repository URL"
  value = {
    for name, repo in aws_ecr_repository.services : name => repo.repository_url
  }
}

output "eso_role_arn" {
  description = "IAM role ARN for External Secrets Operator"
  value       = module.eso_irsa.iam_role_arn
}

output "kubeconfig_command" {
  description = "Command to configure kubectl"
  value       = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${data.aws_region.current.name}"
}
