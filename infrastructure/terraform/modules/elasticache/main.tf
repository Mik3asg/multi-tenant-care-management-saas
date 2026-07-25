// -----------------------------------------------------------------------------
// Cache subnet group — private subnets only
// -----------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.project_name}-redis-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Project = var.project_name
  }
}

// -----------------------------------------------------------------------------
// Security group — ingress from the EKS cluster only
// -----------------------------------------------------------------------------

resource "aws_security_group" "redis" {
  name        = "${var.project_name}-redis-sg"
  description = "Allow Redis access from the EKS cluster only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from EKS"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.eks_cluster_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Project = var.project_name
  }
}

// -----------------------------------------------------------------------------
// Single-node Redis cluster — no replica, matches the plan's cost choice
// -----------------------------------------------------------------------------

resource "aws_elasticache_cluster" "this" {
  cluster_id      = "${var.project_name}-redis"
  engine          = "redis"
  engine_version  = var.engine_version
  node_type       = var.node_type
  num_cache_nodes = 1
  port            = 6379

  subnet_group_name = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.redis.id]

  tags = {
    Project = var.project_name
  }
}
