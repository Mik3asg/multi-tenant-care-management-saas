// -----------------------------------------------------------------------------
// IRSA — one IAM role, federated trust scoped to a single k8s service account
// -----------------------------------------------------------------------------

locals {
  oidc_provider_host = replace(var.cluster_oidc_issuer_url, "https://", "")

  // Avoid a doubled-up name when cluster_name already ends in "-<namespace>"
  // (e.g. cluster_name "carecloudly-production" + namespace "production").
  cluster_name_prefix = trimsuffix(var.cluster_name, "-${var.namespace}")
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:sub"
      values   = ["system:serviceaccount:${var.namespace}:${var.service_account_name}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${local.cluster_name_prefix}-${var.namespace}-${var.service_account_name}"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json

  tags = {
    Namespace      = var.namespace
    ServiceAccount = var.service_account_name
  }
}

resource "aws_iam_role_policy_attachment" "this" {
  # count, not for_each: for_each needs its full set of keys known at plan
  # time, but irsa_eso passes a policy ARN that doesn't exist until apply
  # creates it (aws_iam_policy.eso_secrets_read.arn). count only needs the
  # *length* of policy_arns, which is known even when an element's value isn't.
  count = length(var.policy_arns)

  role       = aws_iam_role.this.name
  policy_arn = var.policy_arns[count.index]
}
