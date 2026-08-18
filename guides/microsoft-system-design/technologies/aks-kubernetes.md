# AKS (Azure Kubernetes Service)

## What It Is
Azure Kubernetes Service (AKS) is a managed Kubernetes container orchestration service. It simplifies deploying, managing, and scaling containerized applications using Kubernetes, with Azure handling the control plane and infrastructure management.

## Kubernetes Basics

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         KUBERNETES ARCHITECTURE                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              Control Plane (Managed by Azure)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                    │
│  │ API Server  │  │ Scheduler   │  │ Controller  │  │   etcd      │                    │
│  │             │  │             │  │ Manager     │  │ (state)     │                    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
                    ▼                   ▼                   ▼
          ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
          │   Worker Node   │ │   Worker Node   │ │   Worker Node   │
          │  ┌───────────┐  │ │  ┌───────────┐  │ │  ┌───────────┐  │
          │  │  Kubelet  │  │ │  │  Kubelet  │  │ │  │  Kubelet  │  │
          │  └───────────┘  │ │  └───────────┘  │ │  └───────────┘  │
          │  ┌───────────┐  │ │  ┌───────────┐  │ │  ┌───────────┐  │
          │  │   Pods    │  │ │  │   Pods    │  │ │  │   Pods    │  │
          │  │ ┌───┐┌───┐│  │ │  │ ┌───┐     │  │ │  │ ┌───┐┌───┐│  │
          │  │ │ C ││ C ││  │ │  │ │ C │     │  │ │  │ │ C ││ C ││  │
          │  │ └───┘└───┘│  │ │  │ └───┘     │  │ │  │ └───┘└───┘│  │
          │  └───────────┘  │ │  └───────────┘  │ │  └───────────┘  │
          └─────────────────┘ └─────────────────┘ └─────────────────┘

KEY CONCEPTS:
  Pod: Smallest deployable unit (1+ containers sharing network/storage)
  Node: Worker machine (VM in AKS)
  Deployment: Manages pod replicas, rolling updates
  Service: Stable network endpoint for pods
  Namespace: Virtual cluster isolation
```

## Creating an AKS Cluster

```bash
# Azure CLI
az aks create \
    --resource-group myResourceGroup \
    --name myAKSCluster \
    --node-count 3 \
    --node-vm-size Standard_DS2_v2 \
    --enable-managed-identity \
    --enable-addons monitoring \
    --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group myResourceGroup --name myAKSCluster

# Verify connection
kubectl get nodes
```

## Deployment Example

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-server
  labels:
    app: api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api
  template:
    metadata:
      labels:
        app: api
    spec:
      containers:
      - name: api
        image: myacr.azurecr.io/api:v1.0.0
        ports:
        - containerPort: 8080
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "500m"
            memory: "512Mi"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 3
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secrets
              key: connection-string
---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: api-service
spec:
  type: LoadBalancer
  selector:
    app: api
  ports:
  - port: 80
    targetPort: 8080
```

```bash
# Deploy
kubectl apply -f deployment.yaml

# Check status
kubectl get pods -l app=api
kubectl get service api-service
```

## Scaling

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SCALING OPTIONS                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. HORIZONTAL POD AUTOSCALER (HPA)
   Scale pods based on CPU/memory/custom metrics
   
   kubectl autoscale deployment api-server --cpu-percent=70 --min=3 --max=10

2. CLUSTER AUTOSCALER
   Scale nodes based on pending pods
   
   az aks update \
     --resource-group myResourceGroup \
     --name myAKSCluster \
     --enable-cluster-autoscaler \
     --min-count 1 \
     --max-count 10

3. VERTICAL POD AUTOSCALER (VPA)
   Adjust resource requests/limits
```

```yaml
# HPA manifest
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-server
  minReplicas: 3
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## Networking

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SERVICE TYPES                                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. ClusterIP (Default)
   Internal only, accessible within cluster
   
2. LoadBalancer
   Exposes via Azure Load Balancer (public IP)
   
3. NodePort
   Exposes on each node's IP at a static port
   
4. Ingress
   HTTP/HTTPS routing, SSL termination

INGRESS EXAMPLE:
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - api.example.com
    secretName: api-tls
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /v1
        pathType: Prefix
        backend:
          service:
            name: api-v1-service
            port:
              number: 80
      - path: /v2
        pathType: Prefix
        backend:
          service:
            name: api-v2-service
            port:
              number: 80
```

## ConfigMaps and Secrets

```yaml
# ConfigMap for non-sensitive config
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  LOG_LEVEL: "info"
  MAX_CONNECTIONS: "100"
  FEATURE_FLAGS: |
    {
      "newUI": true,
      "darkMode": false
    }

---
# Secret for sensitive data
apiVersion: v1
kind: Secret
metadata:
  name: db-secrets
type: Opaque
data:
  connection-string: cG9zdGdyZXM6Ly91c2VyOnBhc3NAaG9zdC9kYg==  # base64 encoded

---
# Using in deployment
spec:
  containers:
  - name: api
    envFrom:
    - configMapRef:
        name: app-config
    env:
    - name: DB_CONNECTION
      valueFrom:
        secretKeyRef:
          name: db-secrets
          key: connection-string
```

## Azure-Specific Features

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         AKS + AZURE INTEGRATION                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. AZURE CONTAINER REGISTRY (ACR)
   az aks update -n myCluster -g myGroup --attach-acr myACR
   
   # Pull images from ACR without explicit credentials
   image: myacr.azurecr.io/myapp:v1

2. AZURE KEY VAULT INTEGRATION
   # Store secrets in Key Vault, mount in pods
   az aks enable-addons --addons azure-keyvault-secrets-provider
   
3. AZURE MONITOR
   # Container insights for logging/metrics
   az aks enable-addons --addons monitoring

4. AZURE AD INTEGRATION
   # RBAC with Azure AD
   az aks update --enable-azure-rbac

5. VIRTUAL NODES (ACI)
   # Burst to Azure Container Instances
   az aks enable-addons --addons virtual-node
```

## Deployment Strategies

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ROLLING UPDATE (Default)                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # Max pods over desired count
      maxUnavailable: 0  # Min pods always running

Time ────────────────────────────────────────────────────────────▶

  v1 v1 v1 v1     Start with 4 pods v1
  v1 v1 v1 v1 v2  Add 1 v2 pod
  v1 v1 v1 v2     Remove 1 v1 pod
  v1 v1 v1 v2 v2  Add another v2
  v1 v1 v2 v2     Remove v1
  ...
  v2 v2 v2 v2     All v2

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         BLUE-GREEN DEPLOYMENT                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐         ┌──────────────┐
  │  Blue (v1)   │ ◀────── │   Service    │
  │  Running     │         │   (selector) │
  └──────────────┘         └──────────────┘
  ┌──────────────┐
  │  Green (v2)  │  ← Deploy v2 alongside v1
  │  Ready       │
  └──────────────┘

  Switch traffic:
  kubectl patch service api-service -p '{"spec":{"selector":{"version":"v2"}}}'
```

## When to Use

✅ **Good for:**
- Microservices architectures
- Containerized applications
- Auto-scaling workloads
- CI/CD pipelines
- Multi-tenant applications

❌ **Consider alternatives for:**
- Simple, single-instance apps (use App Service)
- Serverless event-driven (use Azure Functions)
- Lift-and-shift VMs (use VM Scale Sets)

## Trade-offs

| Pros | Cons |
|------|------|
| Managed control plane | Learning curve |
| Auto-scaling | Complexity |
| Azure integration | Cost at small scale |
| Portable (Kubernetes) | Networking complexity |

## Used In (This Guide)

- **Microservices (Q2)**: Service deployment
- **API Gateway (Q13)**: Backend services
- **CI/CD Pipeline (Q26)**: Deployment target
- **Scalable Services**: Container orchestration
