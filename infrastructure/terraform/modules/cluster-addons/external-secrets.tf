// ESO must be live before cert-manager/external-dns can actually solve
// challenges/create records (docs/infra-plan.md Phase 4 ordering note).
resource "helm_release" "external_secrets" {
  name             = "external-secrets"
  repository       = "https://charts.external-secrets.io"
  chart            = "external-secrets"
  namespace        = "external-secrets"
  create_namespace = true

  // Chart versions move fast — confirm this is still current before applying:
  // helm repo add external-secrets https://charts.external-secrets.io && helm search repo external-secrets/external-secrets
  version = "0.10.5"

  values = [file("${path.root}/../../../../kubernetes/helm/external-secrets/values.yaml")]

  set {
    name  = "serviceAccount.name"
    value = "external-secrets"
  }

  // Binds the k8s service account to the IRSA role via IAM's OIDC trust —
  // this is what lets ESO call secretsmanager:GetSecretValue without static creds.
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = var.eso_irsa_role_arn
  }
}

resource "kubectl_manifest" "cluster_secret_store" {
  yaml_body = yamlencode({
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ClusterSecretStore"
    metadata = {
      name = "aws-secrets-manager"
    }
    spec = {
      provider = {
        aws = {
          service = "SecretsManager"
          region  = var.aws_region
          auth = {
            jwt = {
              serviceAccountRef = {
                name      = "external-secrets"
                namespace = "external-secrets"
              }
            }
          }
        }
      }
    }
  })

  depends_on = [helm_release.external_secrets]
}
