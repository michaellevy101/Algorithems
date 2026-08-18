# HSM (Hardware Security Module)

## What It Is
A Hardware Security Module (HSM) is a dedicated cryptographic processor designed to manage, process, and store cryptographic keys securely. It provides tamper-resistant protection for sensitive operations like encryption, decryption, digital signing, and key management.

## Why HSM?

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SOFTWARE VS HARDWARE KEY STORAGE                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

SOFTWARE KEY STORAGE:
  ┌────────────────────────────────────────────────────────────────┐
  │  Application Server                                            │
  │  ┌─────────────────────────────────────────────────────────┐   │
  │  │  Memory                                                 │   │
  │  │  private_key = load_from_file("/keys/private.pem")     │   │
  │  │  # Key in RAM - vulnerable to:                         │   │
  │  │  # - Memory dumps                                       │   │
  │  │  # - Side-channel attacks                              │   │
  │  │  # - Malware                                           │   │
  │  └─────────────────────────────────────────────────────────┘   │
  └────────────────────────────────────────────────────────────────┘

HSM KEY STORAGE:
  ┌────────────────────────────────────────────────────────────────┐
  │  Application Server              HSM (Tamper-resistant)       │
  │  ┌───────────────────────┐      ┌─────────────────────────┐   │
  │  │  "Sign this data"     │ ───▶ │ Private key NEVER       │   │
  │  │                       │      │ leaves HSM              │   │
  │  │  "Here's signature"   │ ◀─── │                         │   │
  │  └───────────────────────┘      │ Crypto operations done  │   │
  │  Key reference only             │ inside secure hardware  │   │
  │                                  └─────────────────────────┘   │
  └────────────────────────────────────────────────────────────────┘
```

## HSM Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HSM INTERNAL STRUCTURE                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                               HSM Hardware                                               │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐    │
│  │                        Tamper-Resistant Enclosure                                │    │
│  │                        (Zeroizes on physical tamper)                             │    │
│  │                                                                                  │    │
│  │  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐               │    │
│  │  │ Crypto Engine   │   │  Secure Key     │   │  Random Number  │               │    │
│  │  │ - RSA           │   │  Storage        │   │  Generator      │               │    │
│  │  │ - AES           │   │  (Battery-      │   │  (True RNG)     │               │    │
│  │  │ - ECDSA         │   │   backed RAM)   │   │                 │               │    │
│  │  │ - SHA           │   │                 │   │                 │               │    │
│  │  └─────────────────┘   └─────────────────┘   └─────────────────┘               │    │
│  │                                                                                  │    │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    │    │
│  │  │                      Secure API Interface                                │    │    │
│  │  │   PKCS#11 / Microsoft CNG / JCE / REST API                              │    │    │
│  │  └─────────────────────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

TAMPER PROTECTION:
  • Physical intrusion → Zeroize keys
  • Temperature extremes → Zeroize
  • Voltage manipulation → Zeroize
  • X-ray detection → Zeroize
```

## Azure Key Vault (Managed HSM)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         AZURE KEY VAULT OPTIONS                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. KEY VAULT (Standard)
   • Software-protected keys
   • Good for most applications
   • Lower cost

2. KEY VAULT (Premium)
   • HSM-backed keys
   • Keys protected by FIPS 140-2 Level 2 HSMs
   • Keys can't be exported

3. MANAGED HSM
   • Dedicated FIPS 140-2 Level 3 HSM
   • Single-tenant (your HSM only)
   • Full HSM administration control
   • Required for highest compliance

COMPARISON:
┌────────────────────┬───────────┬────────────────┬────────────────┐
│ Feature            │ Standard  │ Premium        │ Managed HSM    │
├────────────────────┼───────────┼────────────────┼────────────────┤
│ Key protection     │ Software  │ HSM (L2)       │ HSM (L3)       │
│ Tenancy            │ Multi     │ Multi          │ Single         │
│ BYOK               │ No        │ Yes            │ Yes            │
│ Key export         │ Yes       │ No             │ Controlled     │
│ Price              │ $         │ $$             │ $$$            │
└────────────────────┴───────────┴────────────────┴────────────────┘
```

## Common Operations

```python
# Azure Key Vault SDK
from azure.identity import DefaultAzureCredential
from azure.keyvault.keys import KeyClient
from azure.keyvault.keys.crypto import CryptographyClient, EncryptionAlgorithm

credential = DefaultAzureCredential()

# Key management
key_client = KeyClient(vault_url="https://myvault.vault.azure.net/", credential=credential)

# Create an RSA key (stored in HSM for Premium tier)
rsa_key = key_client.create_rsa_key("my-rsa-key", size=2048)
print(f"Key created: {rsa_key.name}")

# Create an EC key
ec_key = key_client.create_ec_key("my-ec-key", curve="P-256")

# Cryptographic operations
crypto_client = CryptographyClient(rsa_key, credential=credential)

# Encrypt
plaintext = b"Sensitive data to encrypt"
result = crypto_client.encrypt(EncryptionAlgorithm.rsa_oaep, plaintext)
ciphertext = result.ciphertext

# Decrypt
decrypted = crypto_client.decrypt(EncryptionAlgorithm.rsa_oaep, ciphertext)
print(decrypted.plaintext.decode())  # "Sensitive data to encrypt"

# Sign
from azure.keyvault.keys.crypto import SignatureAlgorithm
import hashlib

digest = hashlib.sha256(b"Document to sign").digest()
signature = crypto_client.sign(SignatureAlgorithm.rs256, digest)

# Verify
verify_result = crypto_client.verify(SignatureAlgorithm.rs256, digest, signature.signature)
print(f"Valid: {verify_result.is_valid}")
```

## Use Cases

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMMON HSM USE CASES                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. PAYMENT PROCESSING
   ┌─────────────────────────────────────────────────────────────────┐
   │  Card Number: 4111-1111-1111-1111                              │
   │                    │                                           │
   │                    ▼                                           │
   │               ┌─────────┐                                      │
   │               │   HSM   │ ← Encrypt with PCI-compliant key    │
   │               └─────────┘                                      │
   │                    │                                           │
   │                    ▼                                           │
   │  Stored: enc(4111-1111-1111-1111, key_in_hsm)                 │
   └─────────────────────────────────────────────────────────────────┘

2. CODE SIGNING
   Developer ──▶ Build artifact ──▶ HSM signs with private key
   Users verify signature with public key (cert from HSM)

3. TLS/SSL CERTIFICATES
   Private key in HSM, never exported
   SSL termination at load balancer uses HSM for signing

4. DATABASE ENCRYPTION (TDE)
   Database Master Key stored in HSM
   Data encrypted with DEK, DEK encrypted with MEK in HSM

5. BLOCKCHAIN / CRYPTOCURRENCY
   Wallet private keys stored in HSM
   Transactions signed by HSM
```

## Key Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         KEY MANAGEMENT HIERARCHY                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

             ┌──────────────────────────────────────────┐
             │  Master Key (Root of Trust)              │
             │  - Stored ONLY in HSM                    │
             │  - Never exported                        │
             │  - Used to wrap other keys               │
             └────────────────────┬─────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
          ┌─────────────────┐         ┌─────────────────┐
          │ Key Encryption  │         │ Key Encryption  │
          │ Key (KEK) 1     │         │ Key (KEK) 2     │
          │ For App 1       │         │ For App 2       │
          └────────┬────────┘         └────────┬────────┘
                   │                           │
         ┌─────────┴─────────┐       ┌─────────┴─────────┐
         │                   │       │                   │
         ▼                   ▼       ▼                   ▼
    ┌─────────┐        ┌─────────┐   ┌─────────┐   ┌─────────┐
    │ DEK 1   │        │ DEK 2   │   │ DEK 3   │   │ DEK 4   │
    │ (data)  │        │ (data)  │   │ (data)  │   │ (data)  │
    └─────────┘        └─────────┘   └─────────┘   └─────────┘
    
DEK = Data Encryption Key (actual data encryption)
KEK = Key Encryption Key (wraps DEKs)
MEK = Master Encryption Key (wraps KEKs)
```

## PKCS#11 Interface

```c
// Standard HSM interface (simplified)

// Initialize
CK_FUNCTION_LIST_PTR funcs;
C_GetFunctionList(&funcs);
funcs->C_Initialize(NULL);

// Open session
CK_SESSION_HANDLE session;
funcs->C_OpenSession(slot, CKF_SERIAL_SESSION | CKF_RW_SESSION, NULL, NULL, &session);

// Login
funcs->C_Login(session, CKU_USER, pin, pin_len);

// Find key
CK_OBJECT_HANDLE key;
CK_ATTRIBUTE template[] = {{ CKA_LABEL, "my-signing-key", 14 }};
funcs->C_FindObjectsInit(session, template, 1);
funcs->C_FindObjects(session, &key, 1, &count);

// Sign data
CK_MECHANISM mech = { CKM_RSA_PKCS, NULL, 0 };
funcs->C_SignInit(session, &mech, key);
funcs->C_Sign(session, data, data_len, signature, &sig_len);

// Cleanup
funcs->C_Logout(session);
funcs->C_CloseSession(session);
funcs->C_Finalize(NULL);
```

## Compliance Standards

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CERTIFICATION LEVELS                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

FIPS 140-2 (Federal Information Processing Standards):

Level 1: Basic security, no physical protection required
         Software module running on GP hardware

Level 2: Tamper-evident seals, role-based authentication
         Physical protection with visible tamper evidence
         
Level 3: Tamper-resistant, identity-based authentication
         Physical protection that actively prevents access
         Zeroizes keys on tamper detection
         
Level 4: Highest security, environmental protection
         Active tamper response to all penetration attempts
         
COMMON REQUIREMENTS:
  • PCI-DSS: Level 3 HSM for cryptographic key operations
  • HIPAA: HSM recommended for PHI encryption
  • Banking (SWIFT): HSM required for transaction signing
```

## When to Use

✅ **Good for:**
- Payment card processing (PCI-DSS)
- Certificate authority operations
- Database encryption key management
- Code/document signing
- Cryptocurrency custody

❌ **May not need if:**
- Non-sensitive data
- Development/testing
- Cost is prohibitive
- Low compliance requirements

## Trade-offs

| Pros | Cons |
|------|------|
| Highest key security | Expensive |
| Compliance (PCI, FIPS) | Latency for operations |
| Tamper protection | Complexity |
| Audit logging | Capacity limits |

## Used In (This Guide)

- **Payment Gateway (Q47)**: Card encryption
- **Authentication (Q8)**: Token signing
- **Certificate Management**: PKI operations
- **Data Protection (Q35)**: Encryption key management
