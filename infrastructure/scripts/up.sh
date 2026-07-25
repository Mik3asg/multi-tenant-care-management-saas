#!/usr/bin/env bash
# Stands up the production environment (VPC + EKS + ECR + IRSA).
# Costs money the moment it succeeds — EKS control plane, node EC2/EBS, NAT gateway.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../terraform/environments/production"

terraform init
terraform apply
