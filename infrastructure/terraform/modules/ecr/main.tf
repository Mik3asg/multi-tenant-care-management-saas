// -----------------------------------------------------------------------------
// ECR repositories
// -----------------------------------------------------------------------------

resource "aws_ecr_repository" "backend" {
  name                 = "care-management/backend"
  image_tag_mutability = "IMMUTABLE"

  // Destroy/recreate is the intended lifecycle for this project (portfolio
  // cost model) — allow terraform destroy to remove the repo even when it
  // still holds images, instead of failing on RepositoryNotEmptyException.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = var.project_name
  }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "care-management/frontend"
  image_tag_mutability = "IMMUTABLE"

  // Destroy/recreate is the intended lifecycle for this project (portfolio
  // cost model) — allow terraform destroy to remove the repo even when it
  // still holds images, instead of failing on RepositoryNotEmptyException.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = var.project_name
  }
}

// -----------------------------------------------------------------------------
// Lifecycle policies — expire untagged images so the repos don't grow forever
// -----------------------------------------------------------------------------

locals {
  untagged_expiry_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.untagged_image_expiry_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_image_expiry_days
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy     = local.untagged_expiry_policy
}

resource "aws_ecr_lifecycle_policy" "frontend" {
  repository = aws_ecr_repository.frontend.name
  policy     = local.untagged_expiry_policy
}
