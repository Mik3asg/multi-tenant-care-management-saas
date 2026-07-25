output "role_arn" {
  description = "ARN of the IAM role — used to annotate the k8s ServiceAccount (eks.amazonaws.com/role-arn)"
  value       = aws_iam_role.this.arn
}

output "role_name" {
  description = "Name of the IAM role"
  value       = aws_iam_role.this.name
}
