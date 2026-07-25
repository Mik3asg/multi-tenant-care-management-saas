// NOTE on bootstrap ordering: this release's pod references the
// "cloudflare-api-token" k8s Secret (via values.yaml), which ESO only
// creates after kubectl_manifest.cloudflare_api_token reconciles — which in
// turn needs this namespace to exist. The pod will CrashLoop briefly on
// first apply until ESO's periodic sync creates the secret and Kubernetes
// restarts it; this is expected, self-heals, and doesn't need manual action.
resource "helm_release" "external_dns" {
  name             = "external-dns"
  repository       = "https://kubernetes-sigs.github.io/external-dns"
  chart            = "external-dns"
  namespace        = "external-dns"
  create_namespace = true

  // Chart versions move fast — confirm this is still current before applying:
  // helm repo add external-dns https://kubernetes-sigs.github.io/external-dns && helm search repo external-dns/external-dns
  version = "1.15.0"

  values = [file("${path.root}/../../../../kubernetes/helm/external-dns/values.yaml")]

  set {
    name  = "domainFilters[0]"
    value = var.cloudflare_zone_name
  }

  set {
    name  = "txtOwnerId"
    value = var.cluster_name
  }
}
