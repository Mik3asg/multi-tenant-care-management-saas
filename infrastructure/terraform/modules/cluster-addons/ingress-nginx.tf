// No external dependencies — safe to apply on its own.
resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true

  // Chart versions move fast — confirm this is still current before applying:
  // helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx && helm search repo ingress-nginx/ingress-nginx
  version = "4.11.3"

  values = [file("${path.root}/../../../../kubernetes/helm/nginx-ingress/values.yaml")]
}
