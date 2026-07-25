variable "project_name" {
  description = "Project name used for naming resources"
  type        = string
}

variable "github_org" {
  description = "GitHub organization/user that owns the repo"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name (without org prefix)"
  type        = string
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs GitHub Actions is allowed to push images to"
  type        = list(string)
}
