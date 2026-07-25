variable "project_name" {
  description = "Project name used for naming/tagging resources"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs the control plane ENIs and node group run in"
  type        = list(string)
}

variable "node_instance_type" {
  description = "EC2 instance type for the managed node group"
  type        = string
}

variable "node_min_size" {
  description = "Minimum number of nodes in the managed node group"
  type        = number
}

variable "node_max_size" {
  description = "Maximum number of nodes in the managed node group"
  type        = number
}

variable "node_desired_size" {
  description = "Desired number of nodes in the managed node group"
  type        = number
}
