#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────
#  Velo WAS — Kubernetes Deploy Script
#  Usage:
#    ./bin/k8s-deploy.sh                   # Apply all manifests
#    ./bin/k8s-deploy.sh --helm            # Install via Helm
#    ./bin/k8s-deploy.sh --delete          # Delete deployment
#    ./bin/k8s-deploy.sh --status          # Show status
# ──────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ACTION="${1:---apply}"

case "$ACTION" in
    --delete)
        echo "Deleting Velo WAS from Kubernetes..."
        kubectl delete -f "$PROJECT_ROOT/deploy/k8s/" --ignore-not-found
        echo "Deleted."
        ;;
    --helm)
        echo "Installing Velo WAS via Helm..."
        helm upgrade --install velo-was "$PROJECT_ROOT/deploy/helm/velo-was/" \
            --namespace velo --create-namespace \
            ${HELM_VALUES:+--values "$HELM_VALUES"}
        echo ""
        echo "Helm release installed. Check status:"
        echo "  helm status velo-was -n velo"
        ;;
    --helm-delete)
        echo "Uninstalling Helm release..."
        helm uninstall velo-was -n velo
        ;;
    --status)
        echo "═══ Pods ═══"
        kubectl get pods -n velo -l app.kubernetes.io/name=velo-was
        echo ""
        echo "═══ Services ═══"
        kubectl get svc -n velo
        echo ""
        echo "═══ HPA ═══"
        kubectl get hpa -n velo 2>/dev/null || echo "No HPA found"
        ;;
    --apply|*)
        echo "Deploying Velo WAS to Kubernetes..."
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/namespace.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/configmap.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/secret.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/pvc.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/deployment.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/service.yaml"
        kubectl apply -f "$PROJECT_ROOT/deploy/k8s/hpa.yaml"
        echo ""
        echo "Deployed! Check status:"
        echo "  kubectl get pods -n velo"
        echo "  kubectl get svc -n velo"
        echo ""
        echo "Access (NodePort):"
        echo "  http://<node-ip>:30080/ai-platform"
        ;;
esac
