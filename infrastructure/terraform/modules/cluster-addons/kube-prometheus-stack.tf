// No external dependencies — safe to apply on its own.
resource "helm_release" "kube_prometheus_stack" {
  name             = "kube-prometheus-stack"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  namespace        = "monitoring"
  create_namespace = true

  // Chart versions move fast — confirm this is still current before applying:
  // helm repo add prometheus-community https://prometheus-community.github.io/helm-charts && helm search repo prometheus-community/kube-prometheus-stack
  version = "66.2.1"

  values = [file("${path.root}/../../../../kubernetes/helm/kube-prometheus-stack/values.yaml")]
}
