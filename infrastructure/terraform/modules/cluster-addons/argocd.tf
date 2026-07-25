// ArgoCD manages only *app* manifests (kubernetes/overlays/production) via
// the Application resource in kubernetes/base/argocd/application.yaml — not
// itself or the other add-ons in this module, which stay Terraform-managed
// (docs/infra-plan.md "Lower-impact defaults chosen").
resource "helm_release" "argocd" {
  name             = "argocd"
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  namespace        = "argocd"
  create_namespace = true

  // Chart versions move fast — confirm this is still current before applying:
  // helm repo add argo https://argoproj.github.io/argo-helm && helm search repo argo/argo-cd
  version = "7.7.10"

  values = [file("${path.root}/../../../../kubernetes/helm/argocd/values.yaml")]
}
