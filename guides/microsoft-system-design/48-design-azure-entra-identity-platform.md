# 48. Design Azure Entra Identity Platform

---

## 0) Requirements & Sizing

### Functional
- **Authentication**: Authenticate users, services, and workloads across VMs, Kubernetes, and external systems
- **Authorization**: Role-Based Access Control (RBAC) with fine-grained permissions
- **Managed Identities**: Passwordless authentication for Azure resources (VMs, AKS, App Services)
- **Workload Identity**: Federated credentials for Kubernetes pods and external workloads (GitHub Actions, AWS)
- **External Identities**: B2B (partners) and B2C (customers) identity federation
- **Conditional Access**: Policy-based access control (location, device, risk level)
- **Single Sign-On (SSO)**: Seamless access across applications
- **Multi-Factor Authentication (MFA)**: Additional security layer
- **Token Management**: OAuth 2.0, OIDC, SAML token issuance and validation

### Non-functional
| Metric | Target |
|--------|--------|
| Authentication latency | **< 100ms** p99 for token validation |
| Token issuance | **< 500ms** for new token acquisition |
| Availability | **99.99%** (four 9s) - identity is critical path |
| Scale | **Millions of authentications/day** per tenant |
| Global | Multi-region with <50ms to nearest endpoint |
| Security | Zero-trust, continuous verification |

### Capacity
```
Large Enterprise Scenario:
- 100,000 employees
- 10,000 VMs with managed identities
- 500 AKS clusters, 50,000 pods with workload identity
- 1M external partners (B2B)
- 10M customers (B2C)

Token operations:
- Each workload: ~100 token acquisitions/day (cached, refreshed hourly)
- Peak: 10M token operations/hour
- Token size: ~2-4KB (JWT)
```

## Design Reasoning

> **Core philosophy**: Zero Trust, passwordless-first identity — never trust, always verify using multi-signal context (user, device, location, risk), with credential-free authentication for Azure resources and OIDC federation for external systems, all built on short-lived tokens with continuous access evaluation.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Azure resource auth | Managed Identities (system/user-assigned) | Eliminates credential storage; tokens from local IMDS at <100ms; automatic lifecycle management | Service Principals with secrets — requires rotation, risk of exposure in CI/CD pipelines |
| Kubernetes auth | Workload Identity with OIDC federation | No secrets in cluster, pod-level granularity, multi-cluster support; replaces deprecated Pod Identity | Pod Identity (NMI DaemonSet) — required secrets in cluster, added networking overhead |
| External workload auth | Federated Identity Credentials (RFC 8693) | Zero credential management for GitHub Actions, AWS, GCP; external OIDC tokens exchanged for Azure tokens | Service Principals with shared secrets — requires rotation, potential exposure in external systems |
| Token lifecycle | 1-hour access tokens + refresh tokens | Minimizes blast radius from compromise; CAE enables immediate revocation on risk signal detection | Long-lived tokens (24h) — increases damage window, delays risk-based revocation |
| Policy engine | Conditional Access (multi-signal) | Implements Zero Trust with real-time evaluation of user, device, location, and risk signals | Static RBAC-only — doesn't account for context, can't respond to real-time threats |
| External identities | B2B guest accounts + B2C for consumers | B2B leverages existing directories; B2C provides 50K free MAU, social login, custom user journeys | Single directory for all — creates identity sprawl; Auth0 — higher cost, less Azure integration |
| Core protocol | OAuth 2.0 / OIDC with JWT tokens | Industry standard, stateless validation via JWKS, supports users + managed identities + workloads | SAML — heavier XML format, less suited for service-to-service, reserved for legacy B2B |
| Availability | Global multi-region distribution | <50ms to nearest endpoint, 99.99% SLA; JWKS cached locally for offline validation | Single-region — geographic latency, failover complexity; Entra's global fabric handles natively |

---

## 1) High-Level Architecture

### Authentication Flow Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          AZURE ENTRA IDENTITY PLATFORM                               │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────────────────┐
│                                IDENTITY SOURCES                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │    Users     │  │   Managed    │  │  Workload    │  │   External   │              │
│  │  (Employees) │  │  Identities  │  │  Identities  │  │  Identities  │              │
│  │              │  │  (VMs, Apps) │  │  (K8s Pods)  │  │  (B2B, B2C)  │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
└─────────┼─────────────────┼─────────────────┼─────────────────┼───────────────────────┘
          │                 │                 │                 │
          │ OIDC/OAuth      │ IMDS            │ OIDC Federation │ SAML/OIDC
          └────────┬────────┴────────┬────────┴────────┬────────┘
                   │                 │                 │
                   ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           AZURE ENTRA ID                                             │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                         TOKEN SERVICE                                         │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │   │
│  │  │ Authentication  │  │ Token Issuance  │  │ Token Validation│              │   │
│  │  │ (Verify creds)  │  │ (JWT signing)   │  │ (Signature check)│             │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                         POLICY ENGINE                                         │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │   │
│  │  │ Conditional     │  │ RBAC            │  │ Risk-Based      │              │   │
│  │  │ Access Policies │  │ Role Assignment │  │ Authentication  │              │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                   │
                   │ Access Token (JWT)
                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              PROTECTED RESOURCES                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │   Azure      │  │   Azure      │  │   Custom     │  │   Microsoft  │            │
│  │   Storage    │  │   SQL DB     │  │   APIs       │  │   Graph API  │            │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘            │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 1 | Identity Source | Azure Entra | Present credentials (password, certificate, token) | SYNC |
| 2 | Azure Entra | Policy Engine | Evaluate conditional access policies | SYNC |
| 3 | Policy Engine | MFA Service | Trigger MFA if required | SYNC |
| 4 | Azure Entra | Token Service | Issue signed JWT access token | SYNC |
| 5 | Client | Resource | Present token in Authorization header | SYNC |
| 6 | Resource | Azure Entra | Validate token signature (cached keys) | SYNC |

---


## API Design

### Authentication APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/oauth2/token` | Get access token (OAuth 2.0) |
| `GET` | `/api/v1/oauth2/authorize` | Authorization endpoint |
| `POST` | `/api/v1/oauth2/revoke` | Revoke a token |
| `GET` | `/api/v1/.well-known/openid-configuration` | OpenID Connect discovery |

### User APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/users/{userId}` | Get user profile |
| `GET` | `/api/v1/users/me` | Get current user (from token) |
| `PATCH` | `/api/v1/users/{userId}` | Update user attributes |
| `POST` | `/api/v1/users` | Create a user |
| `DELETE` | `/api/v1/users/{userId}` | Delete a user |

### Application Registration APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/applications` | Register an application |
| `GET` | `/api/v1/applications/{appId}` | Get application details |
| `PUT` | `/api/v1/applications/{appId}/permissions` | Configure API permissions |

### Token Request (Client Credentials Flow)
```json
POST /api/v1/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=app_123
&client_secret=secret_456
&scope=https://graph.microsoft.com/.default

Response: 200 OK
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "User.Read"
}
```

### Managed Identity APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/metadata/identity/oauth2/token?resource={resource}` | Get token via managed identity (IMDS) |


## 2) Managed Identities for VMs

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        MANAGED IDENTITY FOR VMs                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AZURE VM                                                │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                         APPLICATION CODE                                      │   │
│  │                                                                               │   │
│  │   // No credentials in code!                                                  │   │
│  │   var credential = new DefaultAzureCredential();                             │   │
│  │   var client = new BlobServiceClient(uri, credential);                       │   │
│  │                                                                               │   │
│  └────────────────────────────────────┬─────────────────────────────────────────┘   │
│                                       │ HTTP GET                                     │
│                                       │ http://169.254.169.254/metadata/identity    │
│                                       ▼                                              │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                    INSTANCE METADATA SERVICE (IMDS)                           │   │
│  │                         (169.254.169.254)                                     │   │
│  │                                                                               │   │
│  │   - Local endpoint, no network egress                                        │   │
│  │   - Only accessible from within VM                                           │   │
│  │   - Returns access tokens for assigned identity                              │   │
│  │                                                                               │   │
│  └────────────────────────────────────┬─────────────────────────────────────────┘   │
└───────────────────────────────────────┼─────────────────────────────────────────────┘
                                        │
                                        │ Token request (via Azure Fabric)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AZURE ENTRA ID                                          │
│                                                                                      │
│   ┌─────────────────────────────────────────────────────────────────────────────┐   │
│   │  IDENTITY TYPES:                                                             │   │
│   │                                                                              │   │
│   │  1. SYSTEM-ASSIGNED (Recommended for single-resource)                       │   │
│   │     ├── Created automatically with VM                                        │   │
│   │     ├── Lifecycle tied to VM (deleted when VM deleted)                      │   │
│   │     ├── 1:1 mapping (one identity per VM)                                   │   │
│   │     └── Best for: Single VM accessing specific resources                    │   │
│   │                                                                              │   │
│   │  2. USER-ASSIGNED (Recommended for multiple resources)                      │   │
│   │     ├── Created independently, assigned to multiple VMs                     │   │
│   │     ├── Lifecycle independent of VMs                                        │   │
│   │     ├── M:N mapping (one identity on many VMs, many identities on one VM)  │   │
│   │     └── Best for: Shared workloads, VM scale sets                          │   │
│   └─────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ Access Token
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AZURE RESOURCES                                         │
│                                                                                      │
│   ┌───────────────┐    ┌───────────────┐    ┌───────────────┐                       │
│   │ Azure Storage │    │   Key Vault   │    │   SQL DB      │                       │
│   │               │    │               │    │               │                       │
│   │ RBAC: Storage │    │ RBAC: Key     │    │ RBAC: SQL     │                       │
│   │ Blob Reader   │    │ Vault Secrets │    │ DB Reader     │                       │
│   └───────────────┘    │ User          │    └───────────────┘                       │
│                        └───────────────┘                                             │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Token Acquisition Flow

| Step | Component | Action | Details |
|------|-----------|--------|---------|
| 1 | App Code | Call `DefaultAzureCredential()` | SDK handles token acquisition |
| 2 | SDK | HTTP GET to IMDS | `http://169.254.169.254/metadata/identity/oauth2/token?resource=https://storage.azure.com` |
| 3 | IMDS | Forward to Azure Fabric | Internal Azure infrastructure |
| 4 | Azure Entra | Issue token | Signed JWT with claims |
| 5 | SDK | Cache token | Until 5 min before expiry |
| 6 | SDK | Present to resource | `Authorization: Bearer <token>` |

### Code Example

```csharp
// C# - Using DefaultAzureCredential (recommended)
using Azure.Identity;
using Azure.Storage.Blobs;

// Automatically uses:
// 1. Environment variables (for local dev)
// 2. Managed Identity (when deployed to Azure)
// 3. Azure CLI / Visual Studio credentials (for development)
var credential = new DefaultAzureCredential();

// Access Azure Storage - NO connection strings!
var blobClient = new BlobServiceClient(
    new Uri("https://mystorageaccount.blob.core.windows.net"),
    credential
);

// Access Key Vault - NO secrets in code!
var secretClient = new SecretClient(
    new Uri("https://mykeyvault.vault.azure.net"),
    credential
);
```

```python
# Python - Using DefaultAzureCredential
from azure.identity import DefaultAzureCredential
from azure.storage.blob import BlobServiceClient

credential = DefaultAzureCredential()

# Access Azure Storage
blob_service = BlobServiceClient(
    account_url="https://mystorageaccount.blob.core.windows.net",
    credential=credential
)
```

---

## 3) Workload Identity for Kubernetes (AKS)

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      WORKLOAD IDENTITY FOR AKS                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            AKS CLUSTER                                               │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                              POD                                              │   │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐ │   │
│  │  │                      APPLICATION CONTAINER                               │ │   │
│  │  │                                                                          │ │   │
│  │  │   // Use Azure SDK - same code as VM!                                   │ │   │
│  │  │   var credential = new DefaultAzureCredential();                        │ │   │
│  │  │                                                                          │ │   │
│  │  └──────────────────────────────────┬──────────────────────────────────────┘ │   │
│  │                                     │                                         │   │
│  │   PROJECTED SERVICE ACCOUNT TOKEN   │ Read token from file                   │   │
│  │   /var/run/secrets/azure/tokens/    │                                         │   │
│  │   └── azure-identity-token          │                                         │   │
│  │                                     ▼                                         │   │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐ │   │
│  │  │                    OIDC TOKEN EXCHANGE                                   │ │   │
│  │  │                                                                          │ │   │
│  │  │   K8s Service Account Token ──► Azure Entra Access Token                │ │   │
│  │  │   (signed by AKS OIDC issuer)   (for Azure resources)                   │ │   │
│  │  │                                                                          │ │   │
│  │  └──────────────────────────────────┬──────────────────────────────────────┘ │   │
│  └─────────────────────────────────────┼────────────────────────────────────────┘   │
│                                        │                                             │
│  ┌─────────────────────────────────────▼────────────────────────────────────────┐   │
│  │                    AKS OIDC ISSUER                                            │   │
│  │              https://oidc.prod-aks.azure.com/{tenant}/{cluster}              │   │
│  │                                                                               │   │
│  │   - Issues signed tokens for service accounts                                │   │
│  │   - Public JWKS endpoint for token verification                              │   │
│  │   - Azure Entra trusts this issuer via federation                           │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ OIDC Token Exchange
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           AZURE ENTRA ID                                             │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                    FEDERATED IDENTITY CREDENTIAL                              │   │
│  │                                                                               │   │
│  │   {                                                                           │   │
│  │     "name": "aks-workload-identity",                                         │   │
│  │     "issuer": "https://oidc.prod-aks.azure.com/...",                         │   │
│  │     "subject": "system:serviceaccount:my-namespace:my-service-account",     │   │
│  │     "audience": "api://AzureADTokenExchange"                                 │   │
│  │   }                                                                           │   │
│  │                                                                               │   │
│  │   - Maps K8s service account to Azure AD application                         │   │
│  │   - No secrets stored in cluster                                             │   │
│  │   - Fine-grained: specific namespace + service account                      │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
│                              │                                                       │
│                              ▼                                                       │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                    USER-ASSIGNED MANAGED IDENTITY                             │   │
│  │                    (or App Registration)                                      │   │
│  │                                                                               │   │
│  │   - RBAC roles assigned to this identity                                     │   │
│  │   - Multiple federated credentials can map to same identity                  │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ Access Token
                                        ▼
                              ┌───────────────────┐
                              │  Azure Resources  │
                              └───────────────────┘
```

### Setup Steps

```yaml
# 1. Create User-Assigned Managed Identity
az identity create \
  --name "my-workload-identity" \
  --resource-group "my-rg" \
  --location "eastus"

# 2. Create Federated Credential
az identity federated-credential create \
  --name "aks-federation" \
  --identity-name "my-workload-identity" \
  --resource-group "my-rg" \
  --issuer "https://oidc.prod-aks.azure.com/{tenant-id}/{cluster-id}" \
  --subject "system:serviceaccount:my-namespace:my-service-account" \
  --audience "api://AzureADTokenExchange"

# 3. Assign RBAC Role
az role assignment create \
  --assignee-object-id "<managed-identity-object-id>" \
  --role "Storage Blob Data Reader" \
  --scope "/subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.Storage/storageAccounts/{account}"
```

```yaml
# 4. Kubernetes ServiceAccount (with annotations)
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-service-account
  namespace: my-namespace
  annotations:
    azure.workload.identity/client-id: "<managed-identity-client-id>"
  labels:
    azure.workload.identity/use: "true"
---
# 5. Pod using the ServiceAccount
apiVersion: v1
kind: Pod
metadata:
  name: my-pod
  namespace: my-namespace
  labels:
    azure.workload.identity/use: "true"
spec:
  serviceAccountName: my-service-account
  containers:
  - name: app
    image: myapp:latest
    # Token automatically projected to /var/run/secrets/azure/tokens/
```

### Comparison: Pod Identity vs Workload Identity

| Aspect | Pod Identity (Deprecated) | Workload Identity (Recommended) |
|--------|---------------------------|----------------------------------|
| Secret storage | Identity binding CRDs | No secrets in cluster |
| Token exchange | NMI DaemonSet intercepts | Direct OIDC federation |
| Security | Node-level access | Pod-level, fine-grained |
| Multi-cluster | Complex | Easy (same federation) |
| Non-AKS K8s | No | Yes (any OIDC issuer) |
| Network | Requires NMI pod | No extra pods |

---

## 4) External/Federated Identities

### Architecture for External Workloads

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    FEDERATED IDENTITY FOR EXTERNAL SYSTEMS                           │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL IDENTITY PROVIDERS                                 │
│                                                                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  GitHub Actions │  │   AWS           │  │   GCP           │  │   On-Prem K8s   │ │
│  │                 │  │                 │  │                 │  │                 │ │
│  │  OIDC Issuer:   │  │  OIDC Issuer:   │  │  OIDC Issuer:   │  │  OIDC Issuer:   │ │
│  │  token.actions. │  │  sts.amazonaws. │  │  accounts.      │  │  https://my-    │ │
│  │  githubusercontent│  │  com            │  │  google.com     │  │  cluster/...    │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘ │
│           │                    │                    │                    │          │
│           │ OIDC Token         │ OIDC Token         │ OIDC Token         │ OIDC     │
│           │ (workflow JWT)     │ (IAM Role)         │ (Service Account)  │ Token    │
└───────────┼────────────────────┼────────────────────┼────────────────────┼──────────┘
            │                    │                    │                    │
            └────────────────────┴────────────────────┴────────────────────┘
                                          │
                                          │ Token Exchange (RFC 8693)
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AZURE ENTRA ID                                          │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                    FEDERATED IDENTITY CREDENTIALS                             │   │
│  │                                                                               │   │
│  │   GitHub Actions:                                                             │   │
│  │   {                                                                           │   │
│  │     "issuer": "https://token.actions.githubusercontent.com",                 │   │
│  │     "subject": "repo:myorg/myrepo:ref:refs/heads/main",                     │   │
│  │     "audience": "api://AzureADTokenExchange"                                 │   │
│  │   }                                                                           │   │
│  │                                                                               │   │
│  │   AWS Cross-Cloud:                                                            │   │
│  │   {                                                                           │   │
│  │     "issuer": "https://sts.amazonaws.com",                                   │   │
│  │     "subject": "arn:aws:sts::123456789:assumed-role/MyRole/session",        │   │
│  │     "audience": "api://AzureADTokenExchange"                                 │   │
│  │   }                                                                           │   │
│  │                                                                               │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
│                              │                                                       │
│                              ▼                                                       │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                         APP REGISTRATION                                      │   │
│  │                    (or User-Assigned Managed Identity)                        │   │
│  │                                                                               │   │
│  │   Client ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx                            │   │
│  │   RBAC: Assigned appropriate roles on Azure resources                        │   │
│  │                                                                               │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy to Azure
on:
  push:
    branches: [main]

permissions:
  id-token: write   # Required for OIDC
  contents: read

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      # Login using OIDC - NO secrets stored in GitHub!
      - name: Azure Login
        uses: azure/login@v1
        with:
          client-id: ${{ secrets.AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
      
      # Deploy resources
      - name: Deploy
        run: |
          az webapp deploy --name myapp --src-path ./dist
```

### B2B (Business Partners) Federation

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         B2B IDENTITY FEDERATION                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────┐          ┌───────────────────────────────┐
│     PARTNER TENANT            │          │     YOUR TENANT               │
│     (Contoso)                 │          │     (Fabrikam)                │
│                               │          │                               │
│  ┌─────────────────────────┐  │          │  ┌─────────────────────────┐  │
│  │   User: alice@contoso   │  │          │  │   Guest User Object     │  │
│  │                         │──┼──────────┼─▶│   alice@contoso.com     │  │
│  │   Authenticates at home │  │  B2B     │  │   (shadow account)      │  │
│  │   tenant                │  │  Invite  │  │                         │  │
│  └─────────────────────────┘  │          │  │   RBAC: Reader on       │  │
│                               │          │  │   specific resources    │  │
│                               │          │  └─────────────────────────┘  │
└───────────────────────────────┘          └───────────────────────────────┘
                                                          │
                                                          ▼
                                           ┌───────────────────────────────┐
                                           │   YOUR APPLICATIONS           │
                                           │                               │
                                           │   - SharePoint sites          │
                                           │   - Azure resources           │
                                           │   - Custom apps               │
                                           └───────────────────────────────┘
```

### B2C (Customer) Identity

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         B2C IDENTITY (CUSTOMERS)                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────────────────┐
│                            IDENTITY PROVIDERS                                         │
│                                                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │   Local     │  │   Google    │  │  Facebook   │  │   Apple     │  │   SAML     │ │
│  │   Account   │  │   OAuth     │  │   OAuth     │  │   OAuth     │  │   IdP      │ │
│  │             │  │             │  │             │  │             │  │            │ │
│  │  email +    │  │  "Sign in   │  │  "Sign in   │  │  "Sign in   │  │  Enterprise│ │
│  │  password   │  │  with       │  │  with       │  │  with       │  │  SSO       │ │
│  │             │  │  Google"    │  │  Facebook"  │  │  Apple"     │  │            │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬─────┘ │
└─────────┼────────────────┼────────────────┼────────────────┼────────────────┼────────┘
          │                │                │                │                │
          └────────────────┴────────────────┴────────────────┴────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           AZURE AD B2C TENANT                                        │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                         USER FLOWS                                            │   │
│  │                                                                               │   │
│  │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │   │
│  │   │  Sign-up /      │  │  Password       │  │  Profile        │             │   │
│  │   │  Sign-in        │  │  Reset          │  │  Edit           │             │   │
│  │   │                 │  │                 │  │                 │             │   │
│  │   │  - Collect      │  │  - Email verify │  │  - Update       │             │   │
│  │   │    attributes   │  │  - Set new pwd  │  │    display name │             │   │
│  │   │  - MFA          │  │                 │  │  - Change email │             │   │
│  │   └─────────────────┘  └─────────────────┘  └─────────────────┘             │   │
│  │                                                                               │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                    CUSTOM POLICIES (Advanced)                                 │   │
│  │                                                                               │   │
│  │   - Complex user journeys                                                    │   │
│  │   - REST API integration                                                     │   │
│  │   - Custom token claims                                                      │   │
│  │   - Progressive profiling                                                    │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          │ Access Token + ID Token
                                          ▼
                              ┌───────────────────────────────────────┐
                              │   YOUR CUSTOMER-FACING APPLICATION    │
                              └───────────────────────────────────────┘
```

---

## 5) Token Architecture

### JWT Structure

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              JWT ACCESS TOKEN                                        │
└─────────────────────────────────────────────────────────────────────────────────────┘

HEADER.PAYLOAD.SIGNATURE

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  HEADER (Base64URL encoded)                                                          │
│  {                                                                                   │
│    "typ": "JWT",                                                                    │
│    "alg": "RS256",                                                                  │
│    "kid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  // Key ID for signature verify   │
│  }                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  PAYLOAD (Claims)                                                                    │
│  {                                                                                   │
│    // Standard claims                                                               │
│    "iss": "https://sts.windows.net/{tenant-id}/",     // Issuer                    │
│    "sub": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",     // Subject (user/app ID)     │
│    "aud": "https://storage.azure.com",                // Audience (target resource)│
│    "exp": 1706364000,                                  // Expiration (Unix timestamp)
│    "iat": 1706360400,                                  // Issued at                 │
│    "nbf": 1706360400,                                  // Not before                │
│                                                                                      │
│    // Azure-specific claims                                                         │
│    "tid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",     // Tenant ID                 │
│    "oid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",     // Object ID                 │
│    "azp": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",     // Authorized party (client) │
│    "scp": "user_impersonation",                        // Scopes (delegated)       │
│    "roles": ["Storage.BlobReader"],                    // App roles               │
│                                                                                      │
│    // Optional claims                                                               │
│    "name": "John Doe",                                                             │
│    "email": "john@contoso.com",                                                    │
│    "groups": ["group-id-1", "group-id-2"],            // Group memberships        │
│    "xms_cc": ["CP1"]                                   // Client capabilities      │
│  }                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│  SIGNATURE                                                                           │
│                                                                                      │
│  RS256(Base64URL(header) + "." + Base64URL(payload), private_key)                  │
│                                                                                      │
│  - Signed by Azure Entra's private key                                              │
│  - Verified using public key from JWKS endpoint                                     │
│  - JWKS URL: https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys        │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Token Validation Flow

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          TOKEN VALIDATION FLOW                                      │
└────────────────────────────────────────────────────────────────────────────────────┘

   API Request                                Token Validation
   Authorization: Bearer <token>              (Resource Server)
         │                                           │
         │                                           ▼
         │                              ┌─────────────────────────┐
         │                              │  1. Decode JWT          │
         │                              │     (no crypto yet)     │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  2. Check expiration    │
         │                              │     exp > now ?         │
         │                              │     nbf < now ?         │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  3. Verify issuer (iss) │
         │                              │     Must be Azure Entra │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  4. Verify audience     │
         │                              │     aud == my API ?     │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  5. Fetch JWKS          │
         │                              │     (cached, ~24h)      │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  6. Verify signature    │
         │                              │     using public key    │
         │                              │     from JWKS (kid)     │
         │                              └───────────┬─────────────┘
         │                                          │
         │                                          ▼
         │                              ┌─────────────────────────┐
         │                              │  7. Check claims        │
         │                              │     - roles/scopes      │
         │                              │     - groups            │
         │                              │     - custom claims     │
         │                              └───────────┬─────────────┘
         │                                          │
         ▼                                          ▼
┌─────────────────┐                     ┌─────────────────────────┐
│   API Handler   │◄────────────────────│  Request authorized     │
└─────────────────┘                     └─────────────────────────┘
```

---

## 6) Conditional Access & Zero Trust

### Policy Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         CONDITIONAL ACCESS POLICIES                                  │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              SIGNAL INPUTS                                           │
│                                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │    User      │  │   Location   │  │   Device     │  │    App       │           │
│  │              │  │              │  │              │  │              │           │
│  │  - Identity  │  │  - IP range  │  │  - Compliant │  │  - Target    │           │
│  │  - Group     │  │  - Country   │  │  - Managed   │  │    resource  │           │
│  │  - Role      │  │  - Named loc │  │  - OS type   │  │  - Sensitivity│          │
│  │  - Risk level│  │  - VPN/Corp  │  │  - Join type │  │              │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
└─────────┼─────────────────┼─────────────────┼─────────────────┼────────────────────┘
          │                 │                 │                 │
          └─────────────────┴─────────────────┴─────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           POLICY ENGINE                                              │
│                                                                                      │
│   IF (conditions match) THEN (enforce controls)                                     │
│                                                                                      │
│   ┌─────────────────────────────────────────────────────────────────────────────┐   │
│   │  EXAMPLE POLICIES:                                                           │   │
│   │                                                                              │   │
│   │  Policy 1: "Require MFA for admins"                                         │   │
│   │    IF: User is in "Global Admins" group                                     │   │
│   │    THEN: Require MFA                                                        │   │
│   │                                                                              │   │
│   │  Policy 2: "Block legacy auth"                                              │   │
│   │    IF: Client app uses legacy authentication                                │   │
│   │    THEN: Block access                                                       │   │
│   │                                                                              │   │
│   │  Policy 3: "Require compliant device for sensitive data"                    │   │
│   │    IF: Accessing SharePoint "Confidential" sites                           │   │
│   │    THEN: Require device compliance                                          │   │
│   │                                                                              │   │
│   │  Policy 4: "Block risky sign-ins"                                           │   │
│   │    IF: Sign-in risk = High (Identity Protection)                           │   │
│   │    THEN: Block OR require password change + MFA                            │   │
│   │                                                                              │   │
│   │  Policy 5: "Geo-blocking"                                                   │   │
│   │    IF: Location NOT in allowed countries                                    │   │
│   │    THEN: Block access                                                       │   │
│   └─────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           ACCESS CONTROLS                                            │
│                                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                     │
│  │     GRANT       │  │     SESSION     │  │     BLOCK       │                     │
│  │                 │  │                 │  │                 │                     │
│  │  - Require MFA  │  │  - App enforced │  │  - Deny access  │                     │
│  │  - Require      │  │    restrictions │  │                 │                     │
│  │    compliant    │  │  - Sign-in      │  │                 │                     │
│  │    device       │  │    frequency    │  │                 │                     │
│  │  - Require      │  │  - Persistent   │  │                 │                     │
│  │    approved app │  │    browser      │  │                 │                     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Zero Trust Principles

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           ZERO TRUST MODEL                                           │
│                                                                                      │
│                     "Never trust, always verify"                                    │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │   1. VERIFY EXPLICITLY                                                        │   │
│  │      └── Always authenticate and authorize based on all available data:      │   │
│  │          user identity, location, device health, data classification         │   │
│  │                                                                               │   │
│  │   2. USE LEAST PRIVILEGE ACCESS                                              │   │
│  │      └── Limit user access with Just-In-Time (JIT) and Just-Enough-Access   │   │
│  │          (JEA), risk-based adaptive policies                                 │   │
│  │                                                                               │   │
│  │   3. ASSUME BREACH                                                           │   │
│  │      └── Minimize blast radius, segment access, verify end-to-end            │   │
│  │          encryption, use analytics to detect threats                          │   │
│  │                                                                               │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │                     IMPLEMENTATION WITH AZURE ENTRA                           │   │
│  │                                                                               │   │
│  │   ┌────────────────┐                                                          │   │
│  │   │   IDENTITY     │ ─── Strong auth (MFA, passwordless)                     │   │
│  │   └────────────────┘     Conditional Access policies                          │   │
│  │           │              Identity Protection (risk detection)                 │   │
│  │           ▼                                                                   │   │
│  │   ┌────────────────┐                                                          │   │
│  │   │   DEVICES      │ ─── Device compliance (Intune)                          │   │
│  │   └────────────────┘     Azure AD Join / Hybrid Join                         │   │
│  │           │              Device health attestation                            │   │
│  │           ▼                                                                   │   │
│  │   ┌────────────────┐                                                          │   │
│  │   │   APPS         │ ─── App registration & consent                          │   │
│  │   └────────────────┘     API permissions (scopes, roles)                     │   │
│  │           │              App-only vs delegated access                         │   │
│  │           ▼                                                                   │   │
│  │   ┌────────────────┐                                                          │   │
│  │   │   DATA         │ ─── Data classification (sensitivity labels)           │   │
│  │   └────────────────┘     Information Protection                              │   │
│  │                          DLP policies                                         │   │
│  │                                                                               │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Technical Deep Dive: Nested Group Membership Resolution

### The Problem

Group nesting creates two fundamental challenges on the authentication hot path:

1. **Cycles**: Group A contains Group B, which (directly or indirectly) contains Group A — infinite traversal
2. **Expensive reads**: Resolving all transitive group memberships for a user requires walking an unbounded tree on every login

```
Example: User X is a direct member of Group A

       A
      / \
     B   E
     |   |
     C   |
      \ /
       D

A's members (direct):    B, E
A's members (transitive): B, C, D, E
B's members (transitive): C, D
C's members (transitive): D
E's members (transitive): D

User X is in groups: A, B, C, D, E  (5 groups from 1 direct membership)
```

**Why this matters**: Every token issuance must resolve the user's full group set for:
- Populating JWT `groups` claims
- Evaluating Conditional Access policies (which target groups)
- RBAC role assignment resolution (roles assigned to groups)

A naive approach queries the DB at each level: `O(depth × fan-out)` queries per login. At 200 req/s with an average 5-level hierarchy, that's ~1,000 DB queries/second just for group resolution.

### Scale Analysis: Real Enterprise Numbers

Understanding real-world group scale is critical for choosing the right approach:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    ENTERPRISE GROUP SCALE BY ORG SIZE                             │
├──────────────┬────────────┬──────────────┬───────────────┬──────────────────────┤
│ Metric       │ Small      │ Medium       │ Large         │ Hyperscale           │
│              │ (500 emp)  │ (10K emp)    │ (100K emp)    │ (500K+ emp)          │
├──────────────┼────────────┼──────────────┼───────────────┼──────────────────────┤
│ Total groups │ 100-500    │ 5K-20K       │ 50K-200K      │ 500K-2M              │
│ Groups/user  │ 3-8        │ 10-30        │ 20-80         │ 50-300+              │
│ Nesting depth│ 2-3 levels │ 3-4 levels   │ 4-6 levels    │ 5-7 levels           │
│ Membership   │ 2K-10K     │ 100K-500K    │ 2M-10M        │ 20M-100M+            │
│ rows (total) │            │              │               │                      │
│ Avg fan-out  │ 5-10       │ 10-20        │ 15-50         │ 20-100               │
│ Supernode    │ Rare       │ "All Staff"  │ "All Eng"     │ "All Employees"      │
│ (biggest     │            │ ~10K members │ ~30K members  │ 100K-500K members    │
│  group)      │            │              │               │                      │
│ Auth req/s   │ 5-10       │ 50-200       │ 500-2K        │ 5K-50K               │
│ Group mut/d  │ ~10        │ ~50-200      │ ~500-2K       │ ~5K-20K              │
└──────────────┴────────────┴──────────────┴───────────────┴──────────────────────┘

Key reference: Azure AD/Entra limits
- Max groups per tenant: no hard limit (premium); 500K objects (free tier)
- Max members per group: no hard limit
- Max groups in a token: 200 (then overage claim kicks in)
- Max nesting depth: effectively ~7 levels
- Recommended nesting: ≤3 levels for manageability
```

#### When Single-Node DB Is Sufficient

```
Single PostgreSQL/MySQL instance handles comfortably:
- Up to ~50K groups, ~500K membership rows
- ~200 auth requests/second (with connection pooling)
- Storage: ~500K rows × 100 bytes/row ≈ 50 MB (trivial)
- Index: B-tree on (tenant_id, group_id) fits in RAM

This covers small-to-medium enterprises (up to ~10K employees)
with no sharding, no replication, no distributed cache.
```

#### When Distribution Becomes Necessary

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   DISTRIBUTION THRESHOLDS                                         │
├──────────────────────────┬───────────────────────────────────────────────────────┤
│ Trigger                  │ Solution                                               │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ >500 auth req/s          │ Add read replicas (1 primary + 2-3 replicas)          │
│ (Large enterprise)       │ Route auth reads to replicas, writes to primary       │
│                          │ Replication lag: ~10-50ms (acceptable for groups)     │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ >2K req/s                │ Add Redis cache layer (Option A)                      │
│ OR latency <5ms required │ DB serves as fallback; cache handles 95%+ of reads   │
│                          │ Redis memory: 100K groups × 200 bytes ≈ 20 MB        │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ >10M membership rows     │ Partition by tenant_id                                │
│ (multi-tenant SaaS)      │ Each tenant's data on one shard                      │
│                          │ Hash partition: tenant_id % N → shard                 │
│                          │ Hot tenant? Dedicated shard.                          │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ >50K req/s               │ Full distribution: sharded DB + Redis Cluster         │
│ (hyperscale /            │ + regional read replicas                              │
│  multi-region)           │ Geographic routing: user → nearest region             │
│                          │ Cross-region replication: ~100-200ms lag              │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Supernode problem         │ A single group with 100K+ members creates hotspot   │
│ ("All Employees" group)  │ Solutions:                                            │
│                          │ 1. Shard the group's member list across Redis keys   │
│                          │ 2. Pre-compute: don't traverse through supernodes    │
│                          │ 3. Bitmap index: represent membership as bitfield    │
│                          │ 4. Exclude from transitive closure (special-case)    │
└──────────────────────────┴───────────────────────────────────────────────────────┘

Storage sizing at scale (100K-employee enterprise):
- group_members table:      ~5M rows × 100 bytes  = 500 MB
- transitive_closure table: ~50M rows × 120 bytes = 6 GB
- Redis cache (per-user):   100K users × 50 groups × 40 bytes = 200 MB
- Redis cache (per-group):  100K groups × 200 bytes avg = 20 MB
- Total Redis memory:       ~250 MB (fits on a single node easily)
```

### Read/Write Ratio — The Key Insight

| Operation | Frequency | Actor |
|-----------|-----------|-------|
| Authenticate user (read groups) | ~200/s (millions/day) | Every login |
| Modify group membership (write) | ~100s/day | Admin operations |

**Ratio: ~10,000:1 reads to writes**. This extreme imbalance justifies paying a heavy cost on writes (materialization, cache updates) to make reads O(1).

---

### Option A: Materialized Membership in Redis (Write-Time Computation)

**Core idea**: Pre-compute transitive membership on every group mutation. Store the result in Redis. Reads are a single cache lookup.

#### Cache Schema

```
# Per-group: all transitive members (for policy evaluation)
Key:   group:{tenantId}:{groupId}:members
Value: Redis SET of { memberId }
TTL:   None (invalidated on mutation)

# Per-user: all transitive groups (for token claims)
Key:   user:{tenantId}:{userId}:groups
Value: Redis SET of { groupId }
TTL:   None (invalidated on mutation)

# Group metadata: version for invalidation tracking
Key:   group:{tenantId}:{groupId}:version
Value: Integer (monotonically increasing)
```

#### Write Path: Group Mutation

```
addMember(tenantId, groupId, newMemberId):
    # 1. Cycle detection (MUST happen first)
    if detectCycle(tenantId, groupId, newMemberId):
        return 400 "Adding this member would create a cycle"

    # 2. Acquire distributed lock
    lock = redis.SET("lock:group:{tenantId}:{groupId}", requestId,
                     NX=true, EX=30)  # 30s TTL safety net
    if not lock:
        return 409 "Concurrent modification"

    try:
        # 3. Persist to DB
        db.insert(group_members, {tenantId, groupId, newMemberId})

        # 4. Recompute transitive closure for this group
        newMembers = computeTransitiveClosure(tenantId, groupId)
        redis.DEL("group:{tenantId}:{groupId}:members")
        redis.SADD("group:{tenantId}:{groupId}:members", *newMembers)

        # 5. Propagate: find all ancestor groups and recompute them
        ancestors = findAllAncestors(tenantId, groupId)
        for ancestor in ancestors:
            recomputeAndCache(tenantId, ancestor)

        # 6. Update affected users' group caches
        affectedUsers = findDirectMembers(tenantId, groupId, type=USER)
        for userId in affectedUsers:
            userGroups = computeUserGroups(tenantId, userId)
            redis.DEL("user:{tenantId}:{userId}:groups")
            redis.SADD("user:{tenantId}:{userId}:groups", *userGroups)

        # 7. Increment version
        redis.INCR("group:{tenantId}:{groupId}:version")
    finally:
        redis.DEL("lock:group:{tenantId}:{groupId}")
```

#### Read Path: Token Issuance

```
resolveUserGroups(tenantId, userId):
    groups = redis.SMEMBERS("user:{tenantId}:{userId}:groups")
    if groups is not None:
        return groups  # O(1) cache hit

    # Cache miss: compute from DB and populate cache
    groups = computeUserGroups(tenantId, userId)  # BFS from DB
    redis.SADD("user:{tenantId}:{userId}:groups", *groups)
    return groups
```

#### Cycle Detection

```
detectCycle(tenantId, parentGroupId, candidateMemberId):
    # If candidate is not a group, no cycle possible
    if candidateMemberId.type != GROUP:
        return false

    # BFS: can we reach parentGroupId starting from candidateMemberId?
    visited = Set()
    queue = [candidateMemberId]

    while queue is not empty:
        current = queue.dequeue()
        if current == parentGroupId:
            return true  # Cycle detected!
        if current in visited:
            continue
        visited.add(current)

        # Check cache first, fall back to DB
        children = redis.SMEMBERS("group:{tenantId}:{current}:members")
        if children is None:
            children = db.query(
                "SELECT id FROM group_members
                 WHERE tenantId=? AND group_id=? AND type='group'",
                tenantId, current)

        for child in children:
            if child.type == GROUP:
                queue.enqueue(child)

    return false  # No cycle

    # Complexity: O(V + E) where V = groups in subtree, E = membership edges
    # Bounded by max depth limit (e.g., 7 levels)
```

#### Lazy Invalidation (Optimization)

Instead of full recomputation on every write, use version-based lazy invalidation:

```
# Each cached membership entry tracks the version of its source
# group:{tenantId}:{groupId}:members_versioned -> Hash of {memberId: version}

# On mutation: just increment the group's version (fast)
invalidateGroup(tenantId, groupId):
    redis.INCR("group:{tenantId}:{groupId}:version")

# On read: check if any group in the user's membership chain is stale
resolveWithVersionCheck(tenantId, userId):
    cachedGroups = redis.SMEMBERS("user:{tenantId}:{userId}:groups")
    if cachedGroups is None:
        return fullRecompute(tenantId, userId)

    for groupId in cachedGroups:
        cachedVersion = redis.HGET(
            "user:{tenantId}:{userId}:group_versions", groupId)
        currentVersion = redis.GET(
            "group:{tenantId}:{groupId}:version")

        if cachedVersion != currentVersion:
            # At least one group is stale — recompute
            return fullRecompute(tenantId, userId)

    return cachedGroups  # All versions match, cache is valid
```

**Trade-off**: Lazy invalidation trades slightly stale reads (between mutation and next login) for dramatically cheaper writes. This is acceptable because group changes are admin operations expected to propagate within seconds, not milliseconds.

#### Write Amplification Analysis

```
Worst case: Changing a leaf group in a tree of depth D and fan-out F

Recomputation cost:
- Direct group: 1 recomputation
- Parent:       1 recomputation
- Grandparent:  1 recomputation
- ...up to root: D recomputations total

Each recomputation touches: O(F^D) members in the worst case

Total: O(D × F^D) cache updates for a single group mutation

Example: D=5, F=10 → 5 × 100,000 = 500,000 cache updates

THIS IS WHY DEPTH LIMITS ARE ESSENTIAL (not optional):
- Azure AD/Entra: limits nesting to ~7 levels
- With D=7, F=10: worst case is manageable (~70M) but still heavy
- In practice: most hierarchies are 3-4 levels deep
```

#### Distributed Lock Details

```
Lock scope:     Per-group (fine-grained, allows concurrent mutations
                on different groups)
Technology:     Redis SETNX with EX (expiry)
TTL:            30 seconds (safety net if lock holder crashes)
Contention:     Low — group mutations are rare (~100s/day)

# Handling simultaneous A→B and B→A (concurrent cycle creation):
#   Both operations acquire locks on their respective groups.
#   Both run cycle detection.
#   If A→B completes first, B→A's cycle detection will find A
#   reachable from B and reject.
#
#   Race condition: both cycle detections run simultaneously on
#   the old state and both pass.
#   Solution: Use DB-level serializable transaction for the
#   group_members INSERT. The second INSERT triggers a constraint
#   check that detects the cycle. If using Redis lock only,
#   acquire locks in canonical order (lower groupId first) to
#   prevent deadlock.
```

#### Consistency Model

| Scenario | Consistency | Acceptable? |
|----------|-------------|-------------|
| User logs in after group change | Eventually consistent (~1-5s) | ✅ Yes — admin expects propagation delay |
| User's token already issued, group changed | Stale until token refresh (1h) | ✅ Yes — use Continuous Access Evaluation (CAE) for critical changes |
| Concurrent group mutations on same group | Serialized via distributed lock | ✅ Yes — mutations are rare |
| Cache eviction (Redis restart) | Recomputed on next read (cache miss) | ✅ Yes — latency spike on first request only |

---

### Option B: Graph Database (Neo4j / Azure Cosmos DB Gremlin)

**Core idea**: Group membership IS a graph problem. Use a database purpose-built for graph traversals.

#### Data Model

```
Nodes:
  (:User    {id, tenantId, email, ...})
  (:Group   {id, tenantId, name, ...})
  (:Service {id, tenantId, name, ...})

Edges:
  (User)-[:MEMBER_OF]->(Group)
  (Group)-[:MEMBER_OF]->(Group)        # nested groups
  (Service)-[:MEMBER_OF]->(Group)
  (User|Group|Service)-[:HAS_ROLE {role, resourceType, resourceId}]->(Resource)
```

#### Transitive Membership Query (Cypher / Neo4j)

```cypher
// Resolve all transitive groups for a user (bounded depth)
MATCH (u:User {id: $userId, tenantId: $tenantId})
      -[:MEMBER_OF*1..7]->(g:Group)
RETURN DISTINCT g.id AS groupId

// Cycle detection: check if adding groupB to groupA creates a cycle
MATCH path = (target:Group {id: $groupA})
             -[:MEMBER_OF*1..7]->
             (source:Group {id: $groupB})
RETURN count(path) > 0 AS wouldCreateCycle
```

#### Transitive Membership Query (Gremlin / Cosmos DB)

```groovy
// Resolve all transitive groups for a user
g.V().has('User', 'id', userId)
  .repeat(out('MEMBER_OF')).times(7)
  .emit()
  .hasLabel('Group')
  .dedup()
  .values('id')

// Cycle detection
g.V().has('Group', 'id', groupA)
  .repeat(out('MEMBER_OF')).times(7)
  .until(has('id', groupB))
  .hasNext()
```

#### Architecture with Graph DB

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────────┐
│   Client    │────>│  Auth Service │────>│  Graph DB (Neo4j /  │
│   Browser   │     │              │     │  Cosmos Gremlin)    │
└─────────────┘     │              │     │                     │
                    │  1. Validate  │     │  - Group membership │
                    │     creds    │     │  - Role assignments  │
                    │  2. Query    │     │  - Cycle detection   │
                    │     groups   │     └─────────────────────┘
                    │  3. Evaluate │              │
                    │     policies │     ┌────────┴────────────┐
                    │  4. Issue    │     │  Redis Cache        │
                    │     token   │     │  (query results     │
                    └──────────────┘     │   cached per-user)  │
                                        └─────────────────────┘
                                                 │
                    ┌──────────────┐     ┌────────┴────────────┐
                    │  Admin API   │────>│  RDBMS (MySQL/PG)   │
                    │  (CRUD)      │     │  Users, Apps, etc.  │
                    └──────────────┘     └─────────────────────┘
```

#### Graph DB Considerations

| Factor | Assessment |
|--------|------------|
| **Query naturalness** | ✅ Excellent — traversals are 1-2 lines of Cypher/Gremlin vs complex recursive SQL |
| **Traversal performance** | ✅ O(depth × avg_degree) with index-free adjacency — <10ms for depth ≤7 |
| **Cycle detection** | ✅ Native — path queries handle this without custom BFS |
| **Schema flexibility** | ✅ Easy to add new relationship types (e.g., DELEGATED_TO, OWNS) |
| **ACID transactions** | ⚠️ Neo4j supports full ACID; Cosmos Gremlin has limited transaction scope |
| **Multi-tenancy** | ⚠️ Graph partitioning is NP-hard in general; tenant isolation requires careful design |
| **Sharding/Global distribution** | ❌ Neo4j clustering is limited; Cosmos Gremlin supports global distribution but with RU cost |
| **Operational expertise** | ❌ Most teams lack graph DB experience; hiring and on-call complexity |
| **Cost** | ❌ Cosmos Gremlin: RU cost scales with traversal depth and fan-out (a 7-hop query on a wide tree can consume 100+ RUs) |
| **Ecosystem maturity** | ⚠️ Less tooling, monitoring, and backup solutions compared to RDBMS |

#### Graph DB Limitations at Scale — Concrete Numbers

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   NEO4J LIMITATIONS                                               │
├──────────────────────────┬───────────────────────────────────────────────────────┤
│ Cluster topology         │ Community: single node only (no clustering)           │
│                          │ Enterprise: primary + read replicas (causal cluster)  │
│                          │ No native horizontal sharding until Fabric (v5+)     │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Memory requirements      │ 10M nodes + 50M edges: ~8 GB heap                    │
│                          │ 100M nodes + 500M edges: ~32 GB heap                 │
│                          │ Needs ALL frequently-accessed nodes in page cache     │
│                          │ Cache miss → disk I/O → 10-100x latency increase     │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Supernode problem        │ "All Employees" group: 100K edges from one node      │
│                          │ Traversal THROUGH a supernode: must scan ALL edges   │
│                          │ 100K edges × ~100 bytes = 10 MB scanned per hop      │
│                          │ A 3-hop query through 2 supernodes: ~200ms           │
│                          │ (vs <1ms through normal nodes with 10-20 edges)      │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Multi-tenancy            │ No built-in tenant isolation                          │
│                          │ Options: property-based filtering (slow at scale),   │
│                          │ separate databases per tenant (operational overhead), │
│                          │ or label-based partitioning (limited)                │
│                          │ 1000 tenants × separate DBs = 1000 Neo4j instances   │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Global distribution      │ Read replicas lag: 100-500ms (asynchronous)          │
│                          │ No multi-region write capability                     │
│                          │ Failover: manual promotion, ~30s downtime            │
└──────────────────────────┴───────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│                   COSMOS DB GREMLIN LIMITATIONS                                   │
├──────────────────────────┬───────────────────────────────────────────────────────┤
│ Partition size limit     │ 20 GB per logical partition                           │
│                          │ Partition key = tenantId → large tenant hits limit   │
│                          │ 100K-employee tenant with full graph data: ~5-15 GB  │
│                          │ 500K-employee tenant: may exceed 20 GB limit         │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ RU cost by operation     │ Point read (1 node by ID): ~1 RU                     │
│                          │ 1-hop traversal (10 edges): ~10 RUs                  │
│                          │ 3-hop traversal (100 nodes): ~50-100 RUs             │
│                          │ 7-hop traversal (wide tree, 10K nodes): ~500-2K RUs  │
│                          │ User in 200 groups (resolve all): ~100-300 RUs       │
│                          │                                                       │
│                          │ At 2K auth req/s × 200 RUs avg = 400,000 RU/s       │
│                          │ Cost: ~$2,300/month at $0.008 per 100 RU/s/hr       │
│                          │ (vs ~$500/month for equivalent PostgreSQL + Redis)   │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Cross-partition queries  │ Queries spanning tenants: scatter-gather across all  │
│                          │ partitions. Latency: 50-200ms (vs 5ms in-partition) │
│                          │ RU cost: multiplied by number of partitions touched  │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Transaction scope        │ Single-partition only (all vertices in same          │
│                          │ partition key). No cross-partition transactions.     │
│                          │ Group A (tenant-1) → Group B (tenant-1): OK         │
│                          │ Cross-tenant group nesting: NOT transactional        │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Supernode problem        │ Same as Neo4j: high fan-out = high RU cost           │
│                          │ "All Employees" traversal: 100K edges = ~10K RUs    │
│                          │ Cosmos has no "index-free adjacency" — uses index   │
│                          │ lookups, so supernodes are slightly less painful     │
│                          │ but still expensive (RU-wise, not latency-wise)     │
├──────────────────────────┼───────────────────────────────────────────────────────┤
│ Gremlin feature gaps     │ No support for Gremlin bytecode (text-based only)   │
│                          │ Limited aggregation functions                        │
│                          │ No OLAP workloads (no Spark integration)             │
│                          │ Query timeout: 60 seconds max                        │
└──────────────────────────┴───────────────────────────────────────────────────────┘

Latency comparison at different enterprise scales:

┌──────────────────────────────────────────────────────────────────────────────────┐
│              GRAPH DB LATENCY BY ENTERPRISE SIZE                                  │
│                                                                                  │
│  Latency   Query: "resolve all groups for user X"                                │
│  (ms)                                                                            │
│                                                                                  │
│  100 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─X─ ─   Neo4j (with supernodes) │
│   80 ─                                       /                                   │
│   60 ─                                     /                                     │
│   40 ─                               X──/─ ─ ─ ─ ─ ─   Cosmos Gremlin          │
│   20 ─                          /  /                                             │
│   10 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─X─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   Neo4j (no supernodes)   │
│    5 ─            X──── /                                                        │
│    2 ─ ─ X─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   Redis cached (any)     │
│    1 ─X                                                                          │
│    0 ┬──────────┬──────────┬──────────┬──────────┬──                             │
│      500       10K       100K       500K               Employees                 │
│                                                                                  │
│  Key insight: Graph DBs work great up to ~10K employees with moderate nesting.  │
│  Beyond that, supernodes and fan-out cause degradation. Redis cache eliminates   │
│  this entirely at the cost of eventual consistency.                              │
└──────────────────────────────────────────────────────────────────────────────────┘
```

#### When to Choose Graph DB

- The identity platform is the **primary product** (not a supporting service)
- Team has graph DB expertise or is willing to invest
- Membership hierarchies are deep (>5 levels) and complex
- Multiple relationship types exist beyond simple membership (delegation, ownership, inheritance)
- Query patterns are diverse and unpredictable (not just "get user's groups")

#### Graph DB vs Relational DB: Benchmark Data

Research and benchmarks (2024-2025) consistently show:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│           TRAVERSAL LATENCY: Neo4j vs PostgreSQL (1M-node graph)                 │
│                                                                                  │
│  Source: Neo4j benchmark, IEEE 2024, HelixDB 2025, pgbench.com                  │
│                                                                                  │
│  ┌──────────────┬────────────────┬──────────────────┬──────────────────────────┐ │
│  │ Query Depth  │ Neo4j (Cypher) │ PostgreSQL (CTE) │ Relative                 │ │
│  ├──────────────┼────────────────┼──────────────────┼──────────────────────────┤ │
│  │ 1 hop        │ 1-2 ms         │ 1-3 ms           │ Near-equal               │ │
│  │ 2 hops       │ 2-5 ms         │ 3-6 ms           │ Neo4j ~60% faster        │ │
│  │ 3 hops       │ 5-15 ms        │ 100-2,000 ms     │ Neo4j 10-180x faster     │ │
│  │ 4 hops       │ 10-50 ms       │ 2-30 seconds     │ Neo4j 100-1,000x faster  │ │
│  │ 5+ hops      │ 50-200 ms      │ Minutes/Timeout  │ Neo4j 1,000x+ faster     │ │
│  └──────────────┴────────────────┴──────────────────┴──────────────────────────┘ │
│                                                                                  │
│  WHY? Relational DBs use repeated JOIN + dedup per level. At depth 3 with       │
│  fan-out 20, PostgreSQL executes ~8,000 JOINs. Neo4j follows pointers in O(1)  │
│  per edge (index-free adjacency).                                                │
│                                                                                  │
│  BUT FOR GROUP MEMBERSHIP (depth capped at 7, known structure):                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ 1. Pre-computed transitive closure table → O(1) lookup, ANY database    │    │
│  │ 2. Redis cached sets → O(1) lookup, ~1ms                               │    │
│  │ 3. Neo4j bounded traversal (depth ≤7) → ~5-50ms                        │    │
│  │ 4. PostgreSQL recursive CTE (depth ≤7) → ~10-500ms (depends on data)   │    │
│  │                                                                          │    │
│  │ Key insight: Neo4j's massive advantage is for UNBOUNDED, deep traversal │    │
│  │ For BOUNDED, pre-known structure → pre-computation beats any traversal  │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

#### Why Most Identity Platforms Use Relational DBs (Not Graph)

In practice, **every major identity platform** uses relational databases as their primary store:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                 IDENTITY PLATFORM DATABASE CHOICES                                │
├──────────────────┬───────────────────────────────────────────────────────────────┤
│ Platform         │ Database Architecture                                         │
├──────────────────┼───────────────────────────────────────────────────────────────┤
│ Microsoft Entra  │ Partitioned, geo-replicated proprietary store. Internally    │
│ (Azure AD)       │ modeled as a directory (not a graph DB). Partitioned by      │
│                  │ scale units with primary/secondary replicas.                 │
│                  │ Reads from nearest secondary; writes to primary.             │
│                  │ Transitive membership via /transitiveMembers Graph API       │
│                  │ (computed by traversing DAG, not stored in graph DB).        │
├──────────────────┼───────────────────────────────────────────────────────────────┤
│ Okta / Auth0     │ Relational DB (managed). Custom DB connections support       │
│                  │ MySQL, PostgreSQL, SQL Server. Users, groups, membership     │
│                  │ stored in relational tables — no graph model.                │
├──────────────────┼───────────────────────────────────────────────────────────────┤
│ Keycloak (OSS)   │ PostgreSQL or MySQL. Groups and memberships in relational   │
│                  │ tables with parent_id foreign keys for nesting.              │
├──────────────────┼───────────────────────────────────────────────────────────────┤
│ AWS IAM          │ DynamoDB + proprietary store. Policy evaluation is           │
│                  │ compute-based, not graph-traversal-based.                    │
├──────────────────┼───────────────────────────────────────────────────────────────┤
│ Google Cloud IAM │ Spanner-based. Relational model with strong consistency.     │
│                  │ Group membership resolved via recursive policy evaluation.   │
└──────────────────┴───────────────────────────────────────────────────────────────┘

REASONS relational beats graph for identity:

1. BOUNDED PROBLEM: Group nesting is capped (≤7 levels). The massive advantage
   of graph DBs (1000x at depth 5+) doesn't apply when you pre-compute
   transitive closures or cache results. With pre-computation, RDBMS
   achieves O(1) reads — same as or better than any graph DB traversal.

2. COST: PostgreSQL is free. Neo4j Enterprise: $100K-$900K/year.
   Cosmos Gremlin at 2K req/s: ~$2,300/month. PostgreSQL + Redis: ~$500/month.
   For a startup or mid-size company, this is a 5-20x cost difference.

3. EXPERTISE: ~90% of backend engineers know SQL. <5% have production
   graph DB experience. Hiring, on-call, debugging, and knowledge transfer
   are dramatically easier with PostgreSQL/MySQL.

4. OPERATIONAL MATURITY: RDBMS backup, monitoring, replication, failover
   are battle-tested across millions of deployments. Graph DB tooling is
   improving but still a generation behind.

5. SCHEMA FIT: 95% of identity data (users, apps, policies, roles, tokens)
   is naturally tabular. Only group nesting is graph-like. Adding a
   full graph DB for 5% of the data model is architectural over-engineering.

6. MULTI-TENANCY: Relational partitioning by tenant_id is straightforward.
   Graph partitioning is NP-hard. Neo4j has no native multi-tenant support;
   Cosmos Gremlin is limited to 20 GB per partition.

7. TRANSACTIONS: Group mutations need ACID (detect cycles, update closure,
   reject conflicts). PostgreSQL SERIALIZABLE transactions are rock-solid.
   Neo4j supports ACID but Cosmos Gremlin is single-partition only.
```

#### Neo4j Enterprise Cost Analysis

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   GRAPH DB COST COMPARISON (2024-2025)                            │
│                                                                                  │
│  Source: Neo4j pricing, Vendr.com median deals, Azure Cosmos pricing             │
│                                                                                  │
│  ┌────────────────────┬──────────────────────────────────────────────┐           │
│  │ Option             │ Annual Cost                                  │           │
│  ├────────────────────┼──────────────────────────────────────────────┤           │
│  │ PostgreSQL (RDS)   │ ~$3,000-6,000/yr (db.r6g.large, multi-AZ)  │           │
│  │ + Redis (ElastiC.) │ ~$2,000-4,000/yr (cache.r6g.large)         │           │
│  │ = TOTAL            │ ~$5,000-10,000/yr                           │           │
│  ├────────────────────┼──────────────────────────────────────────────┤           │
│  │ Neo4j AuraDB Pro   │ $65/GB/month → 32 GB = $25,000/yr          │           │
│  │ Neo4j AuraDB Biz   │ $146/GB/month → 32 GB = $56,000/yr         │           │
│  │ Neo4j Enterprise   │ $100,000-900,000/yr (negotiated, median     │           │
│  │ (self-managed)     │  reported: ~$441K/yr)                       │           │
│  ├────────────────────┼──────────────────────────────────────────────┤           │
│  │ Cosmos DB Gremlin  │ 400K RU/s × $0.008/100RU/hr = ~$28,000/yr  │           │
│  │ (2K auth req/s)    │ + storage: ~$2,500/yr                       │           │
│  │ = TOTAL            │ ~$30,000/yr                                 │           │
│  └────────────────────┴──────────────────────────────────────────────┘           │
│                                                                                  │
│  PostgreSQL + Redis is 3-90x cheaper than graph DB alternatives.                │
│  At startup/mid-size scale, this cost difference funds 1-2 engineers.           │
└──────────────────────────────────────────────────────────────────────────────────┘
```

#### Cosmos DB Gremlin Hard Limits (Official)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  Limit                          │ Value              │ Impact                    │
├─────────────────────────────────┼────────────────────┼───────────────────────────┤
│  Max operator depth per query   │ 400 steps          │ Complex traversals may    │
│                                 │                    │ need to be split          │
│  Max .repeat() hops (BFS)       │ 32 hops            │ Fine for groups (≤7)     │
│  Query timeout                  │ 30 seconds         │ Large fan-out may timeout │
│  Script size per request        │ 64 KB              │ Limits query complexity   │
│  Logical partition size         │ 20 GB              │ Large tenants may exceed  │
│  Max partitions queried/request │ 32 partitions      │ Cross-tenant = slow       │
│  Gremlin version                │ 3.4 drivers only   │ No bytecode support       │
│  OLAP support                   │ None               │ Use Spark for analytics   │
│  Transaction scope              │ Single partition    │ No cross-tenant atomicity │
└─────────────────────────────────┴────────────────────┴───────────────────────────┘

RU cost by operation type (measured):
  Point read (1 vertex):                    ~1 RU
  1-hop traversal (10 edges):               ~10 RUs
  3-hop traversal (100 vertices):           ~50-100 RUs
  7-hop traversal (wide tree, 10K nodes):   ~500-2,000 RUs
  User with 200 groups (resolve all):       ~100-300 RUs
  Traversal through supernode (100K edges): ~10,000 RUs (!)

  Key: RU cost is based on ALL vertices/edges TRAVERSED, not just returned.
  A .range(1000,1100) pagination still processes 1,100 elements.
```

---

### Option C: Transitive Closure Table in RDBMS

**Core idea**: Maintain a pre-computed table of all transitive relationships in the relational database itself. No separate cache or graph DB needed.

#### Schema

```sql
-- Source of truth: direct membership only
CREATE TABLE group_members (
    tenant_id   UUID NOT NULL,
    group_id    UUID NOT NULL,
    member_id   UUID NOT NULL,
    member_type ENUM('user', 'group', 'service_principal'),
    PRIMARY KEY (tenant_id, group_id, member_id)
);

-- Materialized: ALL transitive memberships (maintained by application)
CREATE TABLE group_transitive_closure (
    tenant_id     UUID NOT NULL,
    ancestor_id   UUID NOT NULL,  -- the group
    descendant_id UUID NOT NULL,  -- the member (user, group, or SP)
    depth         INT  NOT NULL,  -- hops from ancestor to descendant
    path          TEXT,           -- e.g., "A->B->C->D" (for debugging)
    PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    INDEX idx_descendant (tenant_id, descendant_id)  -- for "user's groups" queries
);
```

#### Read Path

```sql
-- Get all groups a user belongs to (O(1) index lookup)
SELECT ancestor_id AS group_id, depth
FROM group_transitive_closure
WHERE tenant_id = ? AND descendant_id = ?;

-- Check if user is in a specific group (O(1))
SELECT 1 FROM group_transitive_closure
WHERE tenant_id = ? AND ancestor_id = ? AND descendant_id = ?;
```

#### Write Path: Recompute on Mutation

```
addMember(tenantId, groupId, memberId, memberType):
    BEGIN TRANSACTION (SERIALIZABLE)

    # 1. Cycle detection: check if groupId is a descendant of memberId
    cycleExists = db.query(
        "SELECT 1 FROM group_transitive_closure
         WHERE tenant_id=? AND ancestor_id=? AND descendant_id=?",
        tenantId, memberId, groupId)

    if cycleExists:
        ROLLBACK
        return 400 "Cycle detected"

    # 2. Insert direct membership
    db.insert(group_members, {tenantId, groupId, memberId, memberType})

    # 3. Recompute transitive closure
    #    For the new edge (groupId -> memberId):
    #    - All ancestors of groupId now also contain memberId
    #      and all descendants of memberId
    db.execute("""
        INSERT IGNORE INTO group_transitive_closure
            (tenant_id, ancestor_id, descendant_id, depth)
        SELECT
            a.tenant_id,
            a.ancestor_id,          -- ancestors of groupId
            d.descendant_id,        -- descendants of memberId (+ memberId itself)
            a.depth + d.depth + 1
        FROM
            (SELECT tenant_id, ancestor_id, depth
             FROM group_transitive_closure
             WHERE tenant_id=? AND descendant_id=?
             UNION ALL
             SELECT ?, ?, 0) AS a   -- include groupId itself
        CROSS JOIN
            (SELECT tenant_id, descendant_id, depth
             FROM group_transitive_closure
             WHERE tenant_id=? AND ancestor_id=?
             UNION ALL
             SELECT ?, ?, 0) AS d   -- include memberId itself
    """)

    COMMIT
```

#### Transitive Closure Trade-offs

| Factor | Assessment |
|--------|------------|
| **Simplicity** | ✅ Single technology (RDBMS), standard SQL, existing expertise |
| **Read performance** | ✅ O(1) indexed lookups — fastest possible reads |
| **Cycle detection** | ✅ Simple query on the closure table before INSERT |
| **ACID guarantees** | ✅ Full transactional consistency — no stale reads |
| **Write performance** | ⚠️ Recomputing closure on mutation requires cross-join of ancestors × descendants |
| **Storage** | ⚠️ O(V²) worst case — for a group with 10K transitive members, that's 10K rows per ancestor |
| **Depth tracking** | ✅ Built-in depth column enables depth-limited queries |
| **No extra infrastructure** | ✅ Lives in the same DB as users/groups — no Redis or graph DB needed |
| **Global distribution** | ⚠️ Depends on DB replication strategy; eventual consistency across regions |

---

### Alternatives Comparison

| Criteria | Option A: Redis Materialized | Option B: Graph DB | Option C: Transitive Closure Table |
|----------|------------------------------|--------------------|------------------------------------|
| **Read latency** | ~1ms (Redis GET) | ~5-10ms (Neo4j) / ~10-40ms (Cosmos) | ~2-5ms (indexed SQL query) |
| **Write latency** | ~50-500ms (recompute + propagate) | ~5-20ms (single edge insert) | ~20-100ms (closure recompute in TX) |
| **Consistency** | Eventually consistent (~1-5s) | Strong (within single graph TX) | Strong (SERIALIZABLE transaction) |
| **Write amplification** | High — O(D × F^D) cache updates | None — traversal is on-demand | Medium — O(ancestors × descendants) rows |
| **Cycle detection** | Custom BFS implementation | Native query | Simple SQL query on closure table |
| **Infrastructure** | RDBMS + Redis | RDBMS + Graph DB (or standalone) | RDBMS only |
| **Operational complexity** | Medium — Redis cluster management | High — graph DB expertise | Low — standard SQL |
| **Storage (100K emp)** | ~250 MB Redis + 500 MB DB | ~5-15 GB graph + 500 MB RDBMS | ~6.5 GB DB (closure table) |
| **Monthly cost (2K req/s)** | ~$500 (PG + Redis) | ~$2,300 (Cosmos) / ~$1,500 (Neo4j) | ~$400 (PG only) |
| **Global distribution** | ✅ Redis Cluster + replication | ⚠️ Neo4j: no multi-write; Cosmos: RU cost | ⚠️ DB replication dependent |
| **Supernode handling** | ✅ Pre-computed, no traversal | ❌ 100K-edge node: 10MB scan/hop | ✅ Pre-computed in closure table |
| **Scale ceiling** | 50K+ req/s with Redis Cluster | ~2K req/s before RU cost explodes | ~5K req/s with read replicas |
| **Best for** | Ultra-low-latency reads, massive scale | <10K employees, complex queries | Simplicity, strong consistency |

### Recommendation

For an Entra-like identity platform at scale:

**Primary: Option A (Redis Materialized) + Option C (Transitive Closure) as hybrid**

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  WHY HYBRID A+C?                                                                 │
│                                                                                  │
│  At 100K employees / 2K req/s:                                                   │
│    - Transitive closure table: 50M rows, ~6 GB → fits on one PG instance        │
│    - Redis cache: ~250 MB → trivially fits in memory                            │
│    - Combined cost: ~$500/month                                                  │
│                                                                                  │
│  At 500K employees / 10K req/s:                                                  │
│    - Closure table: ~200M rows → partitioned PG (by tenant_id)                  │
│    - Redis: ~1 GB → Redis Cluster with 3 shards                                │
│    - Combined cost: ~$1,500/month                                                │
│                                                                                  │
│  Compare with Graph DB at same scale:                                            │
│    - Cosmos Gremlin: 400K RU/s = ~$2,300/month (and rising with scale)          │
│    - Neo4j Enterprise: ~$1,500/month + supernode latency problems               │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- Use **transitive closure table** as the source of truth in the RDBMS — gives you ACID guarantees, cycle detection via SQL, and serves as the fallback on cache miss
- Use **Redis materialized sets** as the hot-path cache — O(1) reads for token issuance at 2K+ req/s
- On group mutation: update closure table in a SERIALIZABLE transaction, then asynchronously refresh Redis cache
- This gives you the best of both: strong consistency in the DB, ultra-fast reads from cache

**When to choose Graph DB instead**: If the platform serves <10K employees with complex ad-hoc relationship queries beyond group membership (e.g., "find all resources accessible to users who share a group with user X") and the team has graph DB expertise. At >100K employees, supernodes and RU costs make the graph approach increasingly expensive — the hybrid A+C is both cheaper and faster. Note that no major identity platform (Entra, Okta, AWS IAM, Google Cloud IAM) uses a graph database — all use relational or directory-based stores with pre-computed membership resolution.

---

### Token Group Claim Bloat

When a user belongs to many groups (200+), embedding all group IDs in the JWT creates problems:

```
JWT with 200 group IDs:
- Each group ID (UUID): 36 bytes
- 200 groups: ~7,200 bytes just for the groups claim
- Total JWT: ~8-10 KB
- HTTP header limit (many proxies): 8 KB

Result: Token exceeds header limits → authentication fails
```

#### Solution: Groups Overage Claim

```json
// Normal token (≤6 groups): groups included directly
{
  "sub": "user-123",
  "groups": ["group-a", "group-b", "group-c"],
  "roles": ["Reader"],
  "exp": 1700000000
}

// Overage token (>6 groups): pointer to Graph API
{
  "sub": "user-123",
  "_claim_names": { "groups": "src1" },
  "_claim_sources": {
    "src1": {
      "endpoint": "https://graph.microsoft.com/v1.0/me/memberOf"
    }
  },
  "roles": ["Reader"],
  "exp": 1700000000
}
```

**Flow with overage**:
1. Auth service detects user is in >threshold groups (e.g., 6)
2. Token includes `_claim_sources` pointer instead of `groups` array
3. Resource server (API) detects overage claim
4. Resource server calls Graph API to get full group list
5. Resource server caches the result for the token's lifetime

#### Depth Limit Enforcement

```
┌─────────────────────────────────────────────────────────────┐
│  MAX_NESTING_DEPTH = 7  (configurable per tenant)           │
│                                                             │
│  Enforced at:                                               │
│  1. addMember API: reject if adding would exceed depth      │
│  2. Cycle detection: BFS/DFS bounded to MAX_NESTING_DEPTH   │
│  3. Transitive closure: depth column capped                 │
│  4. Graph queries: traversal limit (*1..7)                  │
│                                                             │
│  Why 7?                                                     │
│  - Azure AD uses similar limits in practice                 │
│  - 7 levels with fan-out 10 = 10M nodes (upper bound)      │
│  - Beyond 7: diminishing organizational value,              │
│    exponential cost increase                                │
│                                                             │
│  What happens at the limit:                                 │
│  - API returns 400: "Maximum group nesting depth exceeded"  │
│  - Admin can restructure groups or request limit increase   │
└─────────────────────────────────────────────────────────────┘
```

---

## 7) Security & Governance

| Concern | Solution |
|---------|----------|
| Credential theft | Managed identities (no passwords), passwordless auth (FIDO2, Windows Hello) |
| Token theft | Short-lived tokens (1h), Continuous Access Evaluation (CAE), token binding |
| Lateral movement | Conditional Access, PIM for just-in-time admin access |
| Compromised identity | Identity Protection (risk detection), automated remediation |
| Over-privileged access | RBAC with least privilege, Access Reviews, PIM |
| Audit trail | Sign-in logs, Audit logs, Azure Monitor integration |
| External threats | Geo-blocking, IP restrictions, risky sign-in policies |
| Legacy protocols | Block legacy authentication (IMAP, POP3, SMTP) |

### RBAC Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         RBAC HIERARCHY                                               │
│                                                                                      │
│   Management Groups                                                                  │
│        │                                                                             │
│        ├── Subscription (Production)                                                │
│        │      │                                                                      │
│        │      ├── Resource Group (App-Prod)                                         │
│        │      │      │                                                               │
│        │      │      ├── VM: app-server-1                                           │
│        │      │      │     └── System-Assigned Identity: Reader on Key Vault        │
│        │      │      │                                                               │
│        │      │      ├── Key Vault: app-secrets                                     │
│        │      │      │     └── Access Policy: VM identity can Get Secrets          │
│        │      │      │                                                               │
│        │      │      └── Storage Account: app-storage                               │
│        │      │           └── RBAC: VM identity = Storage Blob Data Reader         │
│        │      │                                                                      │
│        │      └── Resource Group (Data-Prod)                                        │
│        │             └── SQL Database                                                │
│        │                  └── AAD Admin: dba-group@contoso.com                      │
│        │                                                                             │
│        └── Subscription (Development)                                               │
│               └── (Developers have Contributor, not in Prod)                        │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

PRINCIPLE: Assign at lowest scope necessary, use groups not individuals
```

---

## 8) Multi-Region & High Availability

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    AZURE ENTRA GLOBAL ARCHITECTURE                                   │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────────────────┐
│                         AZURE ENTRA ID (Global Service)                               │
│                                                                                       │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐│
│   │                        DISTRIBUTED ACROSS AZURE REGIONS                          ││
│   │                                                                                  ││
│   │   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ ││
│   │   │   East US    │◄──▶│   West US    │◄──▶│  West Europe │◄──▶│  East Asia   │ ││
│   │   │              │    │              │    │              │    │              │ ││
│   │   │  • Auth      │    │  • Auth      │    │  • Auth      │    │  • Auth      │ ││
│   │   │  • Directory │    │  • Directory │    │  • Directory │    │  • Directory │ ││
│   │   │  • Tokens    │    │  • Tokens    │    │  • Tokens    │    │  • Tokens    │ ││
│   │   └──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘ ││
│   │                                                                                  ││
│   │   Replication: Eventually consistent (seconds)                                  ││
│   │   Failover: Automatic, transparent to applications                              ││
│   └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐│
│   │                         AUTHENTICATION ENDPOINTS                                 ││
│   │                                                                                  ││
│   │   login.microsoftonline.com ──► Azure Traffic Manager ──► Nearest datacenter   ││
│   │                                                                                  ││
│   │   Backup endpoints (for resilience):                                            ││
│   │   • login.microsoft.com                                                         ││
│   │   • login.windows.net                                                           ││
│   │   • autologon.microsoftazuread-sso.com (Seamless SSO)                          ││
│   └─────────────────────────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### Resilience Best Practices

| Scenario | Mitigation |
|----------|------------|
| Azure Entra partial outage | Token caching (MSAL default: 1h), fallback to cached credentials |
| Network connectivity | Multiple auth endpoints, retry with exponential backoff |
| Regional failure | Azure Entra auto-routes to healthy region |
| Token validation offline | Cache JWKS keys locally (24h TTL) |

---

## 9) Failure Modes

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Azure Entra outage | Cannot acquire new tokens | Token caching, long-lived refresh tokens (90 days) |
| IMDS unavailable | VMs can't get tokens | Retry logic, circuit breaker, fallback to service principal |
| AKS OIDC issuer down | Pods can't exchange tokens | Cache tokens aggressively, graceful degradation |
| Network partition | Can't reach auth endpoints | Validate tokens locally with cached JWKS |
| Token expired | 401 Unauthorized | Proactive token refresh (5 min before expiry) |
| Credential rotation | Brief auth failures | Blue-green credential rotation, overlap validity period |

### Token Caching Strategy

```csharp
// MSAL handles caching automatically, but you can configure it
var app = ConfidentialClientApplicationBuilder
    .Create(clientId)
    .WithAuthority(authority)
    .WithClientSecret(clientSecret)
    .Build();

// In-memory cache (default) or distributed cache (Redis)
app.AddDistributedTokenCache(services =>
{
    services.AddDistributedMemoryCache(); // Or Redis
    services.Configure<MsalDistributedTokenCacheAdapterOptions>(o =>
    {
        o.AbsoluteExpirationRelativeToNow = TimeSpan.FromHours(24);
    });
});

// Acquire token (uses cache automatically)
var result = await app.AcquireTokenForClient(scopes)
    .ExecuteAsync();
```

---

## 10) Cost Estimates

| Component | Tier | Monthly Cost (Est.) |
|-----------|------|---------------------|
| **Azure Entra ID P1** | Per user/month | ~$6/user |
| **Azure Entra ID P2** | Per user/month (includes P1 + Identity Protection, PIM) | ~$9/user |
| **B2C** | First 50K MAU free, then $0.00325/MAU | Variable |
| **Managed Identities** | Free | $0 |
| **Workload Identity** | Free | $0 |
| **Conditional Access** | Included in P1/P2 | $0 (with P1/P2) |

**Example: 10,000 employee enterprise**
- Azure Entra ID P2: 10,000 × $9 = **$90,000/year**
- Managed Identities: Free
- B2C (1M customers): ~$3,250/month = **$39,000/year**
- **Total: ~$129,000/year**

---

## 11) Trade-offs Discussion

| Decision | Option A | Option B | Choice | Reasoning |
|----------|----------|----------|--------|-----------|
| VM Identity | System-assigned | User-assigned | **Depends** | System for single VM; User for VMSS/shared |
| K8s auth | Pod Identity | Workload Identity | **Workload** | No secrets in cluster, future-proof |
| External workloads | Service Principal + Secret | Federated Credential | **Federated** | No secrets to manage/rotate |
| B2B | Guest accounts | External identities | **Guest** | Full integration with your tenant |
| B2C | Azure AD B2C | 3rd party (Auth0) | **B2C** | Native Azure integration, cost |
| Token lifetime | Short (1h) | Long (24h) | **Short** | Security; use refresh tokens for UX |

---

## 12) 60-Second Explanation

> "Azure Entra ID is Microsoft's cloud identity platform implementing **Zero Trust**: never trust, always verify.
>
> For **Azure VMs**, I use **Managed Identities** - the VM gets an identity automatically, acquires tokens via the Instance Metadata Service at 169.254.169.254, and authenticates to Azure resources without any credentials in code.
>
> For **Kubernetes**, **Workload Identity** federates the AKS OIDC issuer with Azure Entra. Pods get service account tokens that are exchanged for Azure AD tokens - again, no secrets in the cluster.
>
> For **external workloads** like GitHub Actions or AWS, I create **Federated Identity Credentials** that trust external OIDC issuers. The external system presents its token, Azure Entra validates it against the trusted issuer, and issues an Azure token.
>
> For **partners and customers**, **B2B** invites external users as guests (they authenticate at their home tenant), and **B2C** handles consumer identities with social logins.
>
> Everything flows through **Conditional Access** policies that evaluate signals (user, device, location, risk) and enforce controls (MFA, compliance). Combined with short-lived tokens and continuous access evaluation, this implements defense-in-depth."

---

## Technologies Deep Dive

| Technology | Role | Reference |
|------------|------|-----------|
| OAuth 2.0 / OIDC | Token-based authentication protocols | [Tech Guide](technologies/oauth-oidc.md) |
| JWT | Token format for claims | [Tech Guide](technologies/jwt.md) |
| SAML | Legacy federation protocol | [Tech Guide](technologies/saml.md) |
| FIDO2 | Passwordless authentication | [Tech Guide](technologies/fido2.md) |

---

## Appendix: DefaultAzureCredential Chain

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    DefaultAzureCredential - Credential Chain                         │
└─────────────────────────────────────────────────────────────────────────────────────┘

The SDK tries each credential source in order until one succeeds:

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                      │
│  1. ENVIRONMENT VARIABLES                                                           │
│     └── AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID                      │
│     └── Used in: CI/CD pipelines, containers                                       │
│                                                                                      │
│  2. WORKLOAD IDENTITY (AKS)                                                         │
│     └── AZURE_FEDERATED_TOKEN_FILE, AZURE_CLIENT_ID, AZURE_TENANT_ID               │
│     └── Used in: Kubernetes pods with workload identity                            │
│                                                                                      │
│  3. MANAGED IDENTITY                                                                │
│     └── System-assigned or user-assigned                                           │
│     └── Used in: VMs, App Service, Functions, AKS                                  │
│                                                                                      │
│  4. AZURE CLI                                                                       │
│     └── az login credentials                                                       │
│     └── Used in: Local development                                                 │
│                                                                                      │
│  5. AZURE POWERSHELL                                                                │
│     └── Connect-AzAccount credentials                                              │
│     └── Used in: Local development                                                 │
│                                                                                      │
│  6. VISUAL STUDIO                                                                   │
│     └── Logged-in Azure account                                                    │
│     └── Used in: Local development in VS                                           │
│                                                                                      │
│  7. VISUAL STUDIO CODE                                                              │
│     └── Azure Account extension                                                    │
│     └── Used in: Local development in VS Code                                      │
│                                                                                      │
│  8. INTERACTIVE BROWSER                                                             │
│     └── Opens browser for login (if enabled)                                       │
│     └── Used in: Local development, interactive apps                               │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

// Code works everywhere without changes:
var credential = new DefaultAzureCredential();

// Local dev: Uses Azure CLI
// VM: Uses Managed Identity  
// AKS: Uses Workload Identity
// CI/CD: Uses Environment Variables
```

---

## Appendix: Common Scenarios

### Scenario 1: VM accessing Key Vault

```bash
# 1. Enable system-assigned managed identity on VM
az vm identity assign --name myVM --resource-group myRG

# 2. Grant access to Key Vault
az keyvault set-policy --name myKeyVault \
  --object-id $(az vm show --name myVM --resource-group myRG --query identity.principalId -o tsv) \
  --secret-permissions get list

# 3. In VM, app code just works
```

### Scenario 2: AKS pod accessing Storage

```bash
# 1. Create user-assigned managed identity
az identity create --name myWorkloadId --resource-group myRG

# 2. Create federated credential
az identity federated-credential create \
  --name k8s-federation \
  --identity-name myWorkloadId \
  --resource-group myRG \
  --issuer $(az aks show -n myCluster -g myRG --query "oidcIssuerProfile.issuerUrl" -o tsv) \
  --subject system:serviceaccount:default:my-service-account

# 3. Assign RBAC role
az role assignment create \
  --assignee $(az identity show -n myWorkloadId -g myRG --query principalId -o tsv) \
  --role "Storage Blob Data Reader" \
  --scope /subscriptions/.../storageAccounts/mystorageaccount

# 4. Annotate ServiceAccount and deploy
```

### Scenario 3: GitHub Actions deploying to Azure

```bash
# 1. Create app registration
az ad app create --display-name "github-actions-deploy"

# 2. Create federated credential
az ad app federated-credential create --id <app-id> --parameters '{
  "name": "github-main-branch",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:myorg/myrepo:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'

# 3. Assign RBAC role to service principal
az role assignment create \
  --assignee <app-id> \
  --role "Contributor" \
  --scope /subscriptions/.../resourceGroups/myRG

# 4. In GitHub Actions, use azure/login with OIDC
```
