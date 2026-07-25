output "role_arn" {
  description = "IAM role ARN for GitHub Actions to assume via OIDC — set as the AWS_ROLE_ARN repo secret"
  value       = aws_iam_role.github_actions.arn
}
