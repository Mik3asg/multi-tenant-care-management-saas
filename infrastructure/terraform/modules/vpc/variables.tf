variable "project_name" {
  description = "Project Name"
  type        = string
}

variable "cluster_name" {
  description = "Cluster Name"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
}

variable "public_subnet_cidrs" {
  description = "CIDR block for public subnet"
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) == length(var.private_subnet_cidrs)
    error_message = "public_subnet_cidrs and private_subnet_cidrs must have the same length so each AZ gets one public and one private subnet."
  }
}

variable "private_subnet_cidrs" {
  description = "CIDR block for private subnets"
  type        = list(string)
}

