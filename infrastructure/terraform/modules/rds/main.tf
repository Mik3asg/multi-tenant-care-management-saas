// -----------------------------------------------------------------------------
// DB subnet group — private subnets only
// -----------------------------------------------------------------------------

resource "aws_db_subnet_group" "this" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Project = var.project_name
  }
}

// -----------------------------------------------------------------------------
// Security group — ingress from the EKS cluster only
// -----------------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Allow Postgres access from the EKS cluster only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Postgres from EKS"
    from_port       = 5432
    to_port         = 5432
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
// Postgres instance
// -----------------------------------------------------------------------------

resource "aws_db_instance" "this" {
  identifier     = "${var.project_name}-postgres"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_encrypted = true

  db_name  = var.db_name
  username = var.master_username

  // RDS generates and stores the master password in Secrets Manager —
  // it never appears in tfvars, state diffs, or CLI output.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az            = false
  publicly_accessible = false

  backup_retention_period = var.backup_retention_period

  // Portfolio cost model: cluster is destroyed/recreated on demand, so a
  // final snapshot on every destroy isn't wanted. Not the right default for
  // a real production database with real patient data.
  skip_final_snapshot = true

  tags = {
    Project = var.project_name
  }
}
