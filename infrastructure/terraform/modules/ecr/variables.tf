variable "project_name" {
  description = "Project name used for naming/tagging resources"
  type        = string
}

variable "untagged_image_expiry_days" {
  description = "Number of days after which untagged images are expired"
  type        = number
  default     = 7
}
