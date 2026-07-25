terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.33"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.16"
    }
    # kubectl_manifest (not kubernetes_manifest) for CRD-instance resources
    # (ClusterIssuer, ExternalSecret, ClusterSecretStore) — kubernetes_manifest
    # validates against the CRD's schema at plan time, which fails on a fresh
    # cluster where the CRD is installed by the same apply (chicken-and-egg).
    # kubectl_manifest applies raw YAML without that plan-time schema lookup.
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14"
    }
  }
}
