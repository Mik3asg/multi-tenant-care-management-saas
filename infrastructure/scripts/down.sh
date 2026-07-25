#!/usr/bin/env bash
# Tears down the production environment. Leaves only the Phase 1 state
# bucket/lock table and ECR images (repos aren't force-deleted here).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../terraform/environments/production"

terraform destroy
