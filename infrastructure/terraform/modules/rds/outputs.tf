output "db_address" {
  description = "RDS instance hostname (no port)"
  value       = aws_db_instance.this.address
}

output "db_port" {
  description = "RDS instance port"
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "Database name"
  value       = aws_db_instance.this.db_name
}

output "master_user_secret_arn" {
  description = "Secrets Manager ARN holding the RDS-managed master password"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}
