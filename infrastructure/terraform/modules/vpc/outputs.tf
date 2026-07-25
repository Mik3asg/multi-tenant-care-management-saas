// what it needs to output - consumed by eks/ and irsa/ modules

//output "vpc_id"
//output "private_subnet_ids"
//output "public_subnet_ids"

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = aws_subnet.public_subnet[*].id
}

output "private_subnet_ids" {
  description = "List of private subnet IDs"
  value       = aws_subnet.private_subnet[*].id
}