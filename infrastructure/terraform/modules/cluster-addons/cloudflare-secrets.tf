// Materializes the manually-seeded Secrets Manager secret into a k8s Secret
// named "cloudflare-api-token" in each namespace that needs it. The AWS
// secret itself is NOT Terraform-managed (docs/infra-plan.md Phase 4) — seed
// it once via: aws secretsmanager create-secret --name <cloudflare_secret_name> ...

locals {
  cloudflare_secret_namespaces = ["cert-manager", "external-dns"]
}

resource "kubectl_manifest" "cloudflare_api_token" {
  for_each = toset(local.cloudflare_secret_namespaces)

  yaml_body = yamlencode({
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ExternalSecret"
    metadata = {
      name      = "cloudflare-api-token"
      namespace = each.value
    }
    spec = {
      refreshInterval = "1h"
      secretStoreRef = {
        name = "aws-secrets-manager"
        kind = "ClusterSecretStore"
      }
      target = {
        name = "cloudflare-api-token"
      }
      data = [
        {
          secretKey = "api-token"
          remoteRef = {
            key      = var.cloudflare_secret_name
            property = "api-token"
          }
        }
      ]
    }
  })

  depends_on = [kubectl_manifest.cluster_secret_store]
}
