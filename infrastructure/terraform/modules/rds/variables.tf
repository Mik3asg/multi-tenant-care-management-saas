variable "project_name" {
  description = "Project name used for naming/tagging resources"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID the RDS instance and its security group live in"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the DB subnet group"
  type        = list(string)
}

variable "eks_cluster_security_group_id" {
  description = "EKS cluster security group ID — the only allowed ingress source"
  type        = string
}

variable "engine_version" {
  description = "Postgres engine version"
  type        = string
}

variable "instance_class" {
  description = "RDS instance class (burstable, e.g. db.t4g.micro)"
  type        = string
}

variable "allocated_storage" {
  description = "Allocated storage in GB"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Initial database name"
  type        = string
}

variable "master_username" {
  description = "Master username — the password is managed by RDS via Secrets Manager, never set here"
  type        = string
}

variable "backup_retention_period" {
  description = "Automated backup retention period in days"
  type        = number
  default     = 7
}
