output "redis_address" {
  description = "Redis node hostname (no port)"
  value       = aws_elasticache_cluster.this.cache_nodes[0].address
}

output "redis_port" {
  description = "Redis node port"
  value       = aws_elasticache_cluster.this.cache_nodes[0].port
}
