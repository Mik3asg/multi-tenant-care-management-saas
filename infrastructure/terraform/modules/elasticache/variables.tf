variable "project_name" {
  description = "Project name used for naming/tagging resources"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID the ElastiCache cluster and its security group live in"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the cache subnet group"
  type        = list(string)
}

variable "eks_cluster_security_group_id" {
  description = "EKS cluster security group ID — the only allowed ingress source"
  type        = string
}

variable "node_type" {
  description = "ElastiCache node type (e.g. cache.t4g.micro)"
  type        = string
}

variable "engine_version" {
  description = "Redis engine version"
  type        = string
}
