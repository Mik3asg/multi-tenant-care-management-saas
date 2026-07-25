variable "cluster_name" {
  description = "EKS cluster name — used only for naming the IAM role"
  type        = string
}

variable "cluster_oidc_issuer_url" {
  description = "OIDC issuer URL of the EKS cluster (from the eks module)"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the IAM OIDC provider registered for the EKS cluster (from the eks module)"
  type        = string
}

variable "namespace" {
  description = "Kubernetes namespace the service account lives in"
  type        = string
}

variable "service_account_name" {
  description = "Kubernetes service account name this role is federated to"
  type        = string
}

variable "policy_arns" {
  description = "List of IAM policy ARNs to attach to the role"
  type        = list(string)
  default     = []
}
