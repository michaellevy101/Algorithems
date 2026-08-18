# Identity Security Guide — Threat Landscape & Defense

> **🎯 Goal**: Understand why identity is the #1 attack vector, how modern adversaries exploit identity weaknesses, and how to defend AI agents and enterprise systems using CrowdStrike-led identity security practices.

> **⏱ Time**: 1.5–2h reading

> **📦 Deliverable**: A clear understanding of identity-based attack patterns, CrowdStrike's Falcon Identity Security architecture, and a practical defense playbook for securing both human and agent identities.

---

## Table of Contents

1. [Why Identity Is the #1 Attack Vector](#why-identity-is-the-1-attack-vector)
2. [Identity Attack Techniques](#identity-attack-techniques)
3. [The Identity Attack Kill Chain](#the-identity-attack-kill-chain)
4. [CrowdStrike Falcon Next-Gen Identity Security](#crowdstrike-falcon-next-gen-identity-security)
5. [FalconID: Phishing-Resistant Passwordless MFA](#falconid-phishing-resistant-passwordless-mfa)
6. [Protecting AI Agent Identities](#protecting-ai-agent-identities)
7. [Identity Security Architecture](#identity-security-architecture)
8. [Defense Playbook](#defense-playbook)
9. [Interview Angle](#interview-angle)
10. [References & Links](#references--links)

---

## Why Identity Is the #1 Attack Vector

The CrowdStrike 2025 Global Threat Report reveals a fundamental shift: **adversaries no longer need malware — they log in**.

```
┌─────────────────────────────────────────────────────────────────────┐
│              THE IDENTITY CRISIS (CrowdStrike 2025 GTR)              │
│                                                                      │
│   79%  of attacks are MALWARE-FREE                                  │
│         (up from 40% in 2019 — attackers use stolen credentials)    │
│                                                                      │
│   442%  increase in VISHING attacks                                  │
│         (voice phishing, H1→H2 2024)                                │
│                                                                      │
│   50%   increase in ACCESS BROKER activity                           │
│         (criminals selling valid credentials on dark markets)        │
│                                                                      │
│   68%   of identity attacks BYPASS MFA                               │
│         (session hijacking, MFA fatigue, SIM swap)                  │
│                                                                      │
│   48min average BREAKOUT TIME                                        │
│         (from initial access to lateral movement)                   │
│         Fastest observed: 51 SECONDS                                │
│                                                                      │
│   71%   of interactive intrusions involved                           │
│         VALID ACCOUNT abuse                                          │
│                                                                      │
│   136%  increase in CLOUD INTRUSIONS (H1 2025)                      │
│         (misconfigurations + stolen credentials)                     │
│                                                                      │
│  Bottom line: If you control the identity, you control everything.  │
└─────────────────────────────────────────────────────────────────────┘
```

### Why Attackers Prefer Identity Over Malware

| Factor | Malware-Based Attack | Identity-Based Attack |
|--------|---------------------|----------------------|
| **Detection** | EDR catches known signatures/behaviors | Legitimate credentials look normal |
| **Speed** | Must install, execute, evade | Just log in — instant access |
| **Persistence** | Malware can be quarantined | Compromised account persists until revoked |
| **Lateral movement** | Requires exploits for each hop | Valid credentials move freely |
| **Cost to attacker** | Must develop or purchase malware | Buy credentials for ~$10 on dark web |
| **Forensics** | Leaves artifacts (files, processes) | Looks like normal user activity |

---

## Identity Attack Techniques

### 1. Credential Theft & Phishing

```
┌─────────────────────────────────────────────────────────────────────┐
│              CREDENTIAL THEFT LANDSCAPE                               │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ Traditional      │  Phishing email → Fake login page → Harvest   │
│  │ Phishing         │  credentials → Use within minutes             │
│  └─────────────────┘                                                │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ Vishing          │  Call employee as "IT Support" → Talk them    │
│  │ (442% increase)  │  into resetting MFA → Enroll attacker device │
│  └─────────────────┘                                                │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ Adversary-in-    │  Proxy between user and real login → Capture │
│  │ the-Middle       │  session token AFTER MFA → Use token directly│
│  │ (AitM)           │  (Evilginx, Modlishka)                       │
│  └─────────────────┘                                                │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ GenAI-Assisted   │  LLMs craft hyper-personalized phishing      │
│  │ Phishing         │  emails, deepfake voice calls, and fake      │
│  │                  │  video conferencing attacks at scale          │
│  └─────────────────┘                                                │
│                                                                      │
│  ┌─────────────────┐                                                │
│  │ Access Brokers   │  Criminals who specialize in gaining access   │
│  │ (50% increase)   │  and SELLING valid credentials to ransomware │
│  │                  │  operators, nation-states, or other groups    │
│  └─────────────────┘                                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 2. MFA Bypass Techniques (68% Success Rate)

```
┌─────────────────────────────────────────────────────────────────────┐
│              HOW ATTACKERS BYPASS MFA                                 │
│                                                                      │
│  Technique              │ How It Works                               │
│  ───────────────────────┼───────────────────────────────────────── │
│  Session token theft    │ Steal the COOKIE after MFA completes.     │
│  (AitM proxy)           │ Token IS the auth — MFA already passed.  │
│                         │                                           │
│  MFA fatigue            │ Flood user with push notifications until │
│  (push bombing)         │ they tap "Approve" to make it stop.      │
│                         │                                           │
│  SIM swapping           │ Port victim's phone number to attacker's │
│                         │ SIM. Receive SMS OTP codes directly.     │
│                         │                                           │
│  Social engineering     │ Call help desk: "I lost my phone, reset  │
│  (help desk bypass)     │ my MFA." Help desk complies.             │
│                         │                                           │
│  OAuth consent          │ Trick user into granting OAuth consent   │
│  phishing               │ to malicious app. App gets persistent    │
│                         │ access token — no MFA needed for API.    │
│                         │                                           │
│  Recovery code theft    │ Obtain backup codes through phishing     │
│                         │ or data breach. Bypass all MFA.          │
└─────────────────────────────────────────────────────────────────────┘
```

### 3. Lateral Movement (48min Average Breakout)

```
┌─────────────────────────────────────────────────────────────────────┐
│              IDENTITY-BASED LATERAL MOVEMENT                         │
│                                                                      │
│  Timeline of a real attack:                                         │
│                                                                      │
│  T+0:00   Attacker logs in with stolen credentials (looks normal)  │
│  T+0:02   Enumerates Active Directory (BloodHound, SharpHound)     │
│  T+0:05   Discovers service account with Domain Admin membership   │
│  T+0:08   Kerberoasts the service account (offline cracking)       │
│  T+0:15   Cracks weak service account password                     │
│  T+0:18   Uses Domain Admin to access file servers                 │
│  T+0:25   Discovers cloud admin credentials in a config file       │
│  T+0:30   Pivots to Azure/M365 using cloud admin account           │
│  T+0:35   Exfiltrates SharePoint data                              │
│  T+0:45   Deploys ransomware across domain                         │
│  T+0:48   ← Average breakout time                                  │
│                                                                      │
│  Key: Every step used VALID CREDENTIALS. No malware involved       │
│  until ransomware deployment at the very end.                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## The Identity Attack Kill Chain

```
┌──────────────────────────────────────────────────────────────────────┐
│                 IDENTITY ATTACK KILL CHAIN                            │
│                                                                       │
│  ┌───────────┐    ┌──────────────┐    ┌─────────────────┐           │
│  │ 1. OBTAIN │───►│ 2. INITIAL   │───►│ 3. PRIVILEGE    │           │
│  │ CREDENTIAL│    │    ACCESS     │    │    ESCALATION   │           │
│  │           │    │              │    │                 │           │
│  │ • Phish   │    │ • VPN login  │    │ • Kerberoast   │           │
│  │ • Vish    │    │ • RDP        │    │ • Pass-the-hash│           │
│  │ • Buy     │    │ • Cloud      │    │ • Token theft  │           │
│  │ • Steal   │    │   console    │    │ • Misconfig    │           │
│  └───────────┘    └──────────────┘    └────────┬────────┘           │
│                                                │                     │
│                                                ▼                     │
│  ┌───────────┐    ┌──────────────┐    ┌─────────────────┐           │
│  │ 6. IMPACT │◄───│ 5. DATA      │◄───│ 4. LATERAL      │           │
│  │           │    │    ACCESS     │    │    MOVEMENT      │           │
│  │ • Ransom  │    │              │    │                 │           │
│  │ • Exfil   │    │ • SharePoint │    │ • RDP           │           │
│  │ • Destroy │    │ • OneDrive   │    │ • SMB           │           │
│  │ • Persist │    │ • Databases  │    │ • Cloud pivot   │           │
│  └───────────┘    └──────────────┘    └─────────────────┘           │
│                                                                       │
│  Detection opportunity windows:                                      │
│  • Step 1-2: Minutes (if you have ITDR with behavioral analytics)   │
│  • Step 3-4: Seconds (if you have real-time lateral movement alerts) │
│  • Step 5-6: Too late (data is already accessed/exfiltrated)        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## CrowdStrike Falcon Next-Gen Identity Security

CrowdStrike's Falcon platform provides unified identity security across the entire attack chain. Here's the architecture:

```
┌─────────────────────────────────────────────────────────────────────┐
│         FALCON NEXT-GEN IDENTITY SECURITY ARCHITECTURE               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    UNIFIED FALCON PLATFORM                      │ │
│  │                                                                 │ │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  │ │
│  │  │ Endpoint  │  │ Identity  │  │ Cloud     │  │ Next-Gen  │  │ │
│  │  │ Security  │  │ Security  │  │ Security  │  │ SIEM      │  │ │
│  │  │ (EDR/XDR) │  │ (ITDR)   │  │ (CNAPP)   │  │           │  │ │
│  │  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  │ │
│  │        │              │              │              │          │ │
│  │        └──────────────┴──────────────┴──────────────┘          │ │
│  │                           │                                     │ │
│  │                    ┌──────▼──────┐                              │ │
│  │                    │ THREAT      │  Cross-domain correlation:  │ │
│  │                    │ GRAPH       │  identity + endpoint +      │ │
│  │                    │             │  cloud + SaaS telemetry     │ │
│  │                    └──────┬──────┘                              │ │
│  │                           │                                     │ │
│  │              ┌────────────┼────────────┐                       │ │
│  │              ▼            ▼            ▼                        │ │
│  │        ┌──────────┐ ┌──────────┐ ┌──────────┐                 │ │
│  │        │ Detect   │ │ Prevent  │ │ Respond  │                 │ │
│  │        │          │ │          │ │          │                 │ │
│  │        │ Anomalous│ │ Block    │ │ Auto-    │                 │ │
│  │        │ logins,  │ │ lateral  │ │ isolate, │                 │ │
│  │        │ priv esc,│ │ movement,│ │ force    │                 │ │
│  │        │ unusual  │ │ enforce  │ │ re-auth, │                 │ │
│  │        │ behavior │ │ MFA      │ │ revoke   │                 │ │
│  │        └──────────┘ └──────────┘ └──────────┘                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  Single lightweight agent → No separate identity agent needed       │
│  Real-time → Sub-second detection and enforcement                   │
│  Agentic AI → Automated triage, investigation, and response        │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | What It Does | Why It Matters |
|-----------|-------------|----------------|
| **Identity Threat Detection & Response (ITDR)** | Real-time monitoring of authentication events, credential usage, and privilege changes across AD, Entra, Okta, and SaaS apps | Detects credential misuse before lateral movement succeeds |
| **FalconID** | Phishing-resistant, passwordless MFA using FIDO2 + endpoint/identity risk signals | Eliminates the #1 attack vector (stolen passwords + MFA bypass) |
| **Privileged Access Management** | Zero-standing-privileges: JIT access grants, auto-revocation, risk-based approval | Eliminates permanently over-privileged accounts |
| **Identity-Driven Case Management** | Auto-correlates identity + endpoint + cloud detections into unified investigation cases | Full attack chain visibility in one place |
| **Agentic AI for Enforcement** | Autonomous AI agents that triage alerts, investigate context, and take enforcement actions | Reduces human SOC analyst burden, sub-second response |

### How ITDR Works (Detection Pipeline)

```
Data Sources:                  Falcon ITDR Engine:            Actions:
                                                              
Active Directory ────┐         ┌──────────────────┐         ┌──────────────┐
  (Kerberos,         │         │ Behavioral        │         │ Block auth   │
   LDAP, NTLM)      │         │ Analytics:         │         │              │
                     │         │                   │         │ Force MFA    │
Microsoft Entra ─────┤    ────►│ • Impossible      │────────►│              │
  (OAuth, SAML,      │    │   │   travel           │         │ Isolate      │
   OIDC)             │    │   │ • Unusual time     │         │ endpoint     │
                     ├────┘   │ • Privilege        │         │              │
Okta / Ping ─────────┤        │   escalation       │         │ Revoke       │
                     │        │ • Service acct     │         │ session      │
Cloud / SaaS ────────┤        │   anomaly          │         │              │
  (M365, AWS,        │        │ • Lateral move     │         │ Create case  │
   Salesforce)       │        │   patterns         │         │ in SIEM      │
                     │        │ • Non-human ID     │         │              │
Endpoint telemetry ──┘        │   behavior change  │         │ Alert SOC    │
                              └──────────────────┘         └──────────────┘
```

---

## FalconID: Phishing-Resistant Passwordless MFA

FalconID is CrowdStrike's answer to the MFA bypass problem (68% of attacks bypass traditional MFA).

```
┌─────────────────────────────────────────────────────────────────────┐
│              FalconID vs TRADITIONAL MFA                              │
│                                                                      │
│  Traditional MFA:                                                    │
│  ┌────────┐    ┌─────────┐    ┌──────────────┐                     │
│  │Password │───►│ SMS OTP │───►│ Access       │                     │
│  │(stolen) │    │(SIM swap│    │ Granted      │                     │
│  │         │    │ AitM)   │    │              │                     │
│  └────────┘    └─────────┘    └──────────────┘                     │
│                                                                      │
│  Problems: Password can be phished. SMS can be intercepted.         │
│  Push can be fatigue-bombed. Session token can be stolen.           │
│                                                                      │
│  ─────────────────────────────────────────────────────────────      │
│                                                                      │
│  FalconID (FIDO2 + Risk Signals):                                   │
│  ┌─────────────┐    ┌──────────────────┐    ┌──────────────┐       │
│  │ Biometric /  │───►│ Risk Engine:     │───►│ Access       │       │
│  │ Hardware key │    │ • Endpoint health│    │ Granted      │       │
│  │ (FIDO2)     │    │ • User behavior  │    │ (or Denied)  │       │
│  │             │    │ • Device posture │    │              │       │
│  │ Crypto proof│    │ • Network context│    │              │       │
│  │ never leaves│    │ • Identity risk  │    │              │       │
│  │ the device  │    │   score          │    │              │       │
│  └─────────────┘    └──────────────────┘    └──────────────┘       │
│                                                                      │
│  Why phishing-resistant:                                            │
│  • NO password to steal (passwordless)                              │
│  • NO OTP to intercept (cryptographic challenge-response)           │
│  • NO session to hijack (bound to device + endpoint telemetry)      │
│  • Origin-bound: credential is TIED to the real login URL           │
│    (fake URL → FIDO2 won't respond → attack fails)                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Protecting AI Agent Identities

AI agents are what CrowdStrike calls **"superhuman identities"** — they operate 24/7, often with elevated privileges, and are increasingly targeted.

```
┌─────────────────────────────────────────────────────────────────────┐
│              AI AGENTS AS ATTACK SURFACE                              │
│                                                                      │
│  Why agents are high-value targets:                                 │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  • Run 24/7 with persistent credentials                      │   │
│  │  • Often have broad access (read databases, call APIs,       │   │
│  │    modify files, send emails)                                 │   │
│  │  • May have elevated privileges (admin-level for automation) │   │
│  │  • Less monitored than human accounts                        │   │
│  │  • Credential rotation may be neglected                      │   │
│  │  • Actions harder to distinguish from legitimate operations  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Attack scenarios:                                                  │
│                                                                      │
│  1. Compromised agent credential → Attacker uses agent's           │
│     permissions to access all connected resources silently          │
│                                                                      │
│  2. Prompt injection → Malicious input tricks agent into            │
│     executing unauthorized tool calls with its elevated access      │
│                                                                      │
│  3. MCP server compromise → Attacker controls the tool endpoint    │
│     the agent trusts, intercepting data and injecting responses     │
│                                                                      │
│  4. Agent sprawl → Forgotten agents with stale credentials          │
│     become unmonitored backdoors into the network                   │
└─────────────────────────────────────────────────────────────────────┘
```

### Defending Agent Identities

| Defense | How To Implement | CrowdStrike Role |
|---------|-----------------|------------------|
| **Use secretless auth** | Managed Identity, Workload ID Federation, SPIFFE (see [Authentication Guide](appendix-agent-authentication.md)) | N/A — infrastructure-level |
| **Monitor non-human behavior** | Baseline agent activity patterns; alert on anomalies (new APIs, new IPs, unusual volumes) | Falcon ITDR detects non-human identity anomalies |
| **Least privilege** | Each agent gets only the permissions it needs. Review quarterly | Falcon PAM enforces zero-standing-privileges |
| **Lifecycle management** | Inventory all agents. Decommission unused ones. Track owner and purpose | Entra Agent ID + Falcon catalog integration |
| **Conditional Access** | Block agent access from unexpected networks, times, or geolocations | Entra Conditional Access + Falcon endpoint posture |
| **Audit everything** | Log every agent action with traceable identity | Falcon SIEM + Entra audit logs |
| **Rotate credentials** | If secrets are used, rotate frequently (<90 days). Prefer FIC | Falcon alerts on long-lived credentials |

---

## Identity Security Architecture

A modern identity security architecture follows **zero trust** principles:

```
┌─────────────────────────────────────────────────────────────────────┐
│              ZERO TRUST IDENTITY ARCHITECTURE                        │
│                                                                      │
│  Principle: "Never trust, always verify. Assume breach."            │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  1. IDENTITY VERIFICATION (every request)                   │     │
│  │     • Who is requesting? (human, agent, service)           │     │
│  │     • Is the credential valid? (not expired, not revoked)  │     │
│  │     • Is MFA satisfied? (FIDO2/FalconID for humans)        │     │
│  │     • Is the device healthy? (endpoint posture check)      │     │
│  └────────────────────────────────────────────────────────────┘     │
│                         │                                            │
│                         ▼                                            │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  2. RISK-BASED ACCESS (dynamic, not static)                │     │
│  │     • Conditional Access: location, device, risk score     │     │
│  │     • JIT privileges: grant for 1 hour, then auto-revoke  │     │
│  │     • Step-up auth: sensitive operations require re-verify │     │
│  └────────────────────────────────────────────────────────────┘     │
│                         │                                            │
│                         ▼                                            │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  3. CONTINUOUS MONITORING (not just at login)               │     │
│  │     • Session behavior analytics                           │     │
│  │     • Anomaly detection (unusual time, geo, volume)        │     │
│  │     • Cross-domain correlation (identity + endpoint +      │     │
│  │       cloud + SaaS)                                         │     │
│  │     • Real-time enforcement (block, re-auth, isolate)      │     │
│  └────────────────────────────────────────────────────────────┘     │
│                         │                                            │
│                         ▼                                            │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  4. BLAST RADIUS MINIMIZATION                              │     │
│  │     • Microsegmentation: limit what each identity can see  │     │
│  │     • Zero-standing-privileges: no permanent admin access  │     │
│  │     • Auto-containment: isolate compromised identities     │     │
│  └────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Defense Playbook

### Practical Controls by Priority

| Priority | Control | Implementation | Addresses |
|----------|---------|---------------|-----------|
| **P0** | Deploy phishing-resistant MFA (FIDO2) | FalconID, Windows Hello, YubiKey | 68% MFA bypass, all phishing |
| **P0** | Eliminate standing privileges | JIT access via PAM, auto-revoke after use | Privilege escalation, lateral movement |
| **P1** | Monitor identity telemetry | Falcon ITDR across AD, Entra, Okta, SaaS | All identity-based attacks |
| **P1** | Protect service accounts | Rotate passwords, monitor usage, restrict login sources | Kerberoasting, lateral movement |
| **P1** | Inventory non-human identities | Catalog all agents, service accounts, API keys. Track owners | Agent sprawl, orphaned credentials |
| **P2** | Enforce Conditional Access | Block legacy auth, require device compliance, geo-restrict | Account takeover from unexpected locations |
| **P2** | Detect lateral movement | Cross-domain correlation (endpoint + identity + network) | 48min breakout window attacks |
| **P2** | Secure help desk processes | Require identity verification before MFA resets | Vishing (442% increase) |
| **P3** | Train users on vishing | Regular simulated phishing + vishing campaigns | Social engineering |
| **P3** | Monitor access broker markets | Threat intelligence feeds for leaked credentials | Credential purchasing |

### Detection Strategies

```
┌─────────────────────────────────────────────────────────────────────┐
│              HIGH-SIGNAL IDENTITY DETECTIONS                         │
│                                                                      │
│  Detection                      │ Signal                            │
│  ───────────────────────────────┼────────────────────────────────── │
│  Impossible travel               │ Login from NYC, then London 10   │
│                                 │ minutes later                     │
│                                 │                                   │
│  Dormant account activation     │ Service account unused for 90    │
│                                 │ days suddenly makes API calls    │
│                                 │                                   │
│  Privilege escalation           │ Non-admin account adds self to   │
│                                 │ Domain Admins group              │
│                                 │                                   │
│  Unusual authentication method  │ Account that always uses SSO     │
│                                 │ suddenly uses password + OTP     │
│                                 │                                   │
│  Token anomaly                  │ OAuth token used from different  │
│                                 │ IP than where it was issued      │
│                                 │                                   │
│  Mass enumeration               │ One account queries 500+ AD     │
│                                 │ objects in 60 seconds            │
│                                 │                                   │
│  Agent behavior change          │ AI agent suddenly accesses       │
│                                 │ resources outside its normal     │
│                                 │ pattern (new APIs, new tables)   │
│                                 │                                   │
│  Cross-domain pivot             │ On-prem credentials used to      │
│                                 │ access cloud resources for       │
│                                 │ first time (hybrid attack)       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Interview Angle

### Key Points to Articulate

1. **"Identity is the new perimeter"** — 79% malware-free means endpoint-only security is insufficient. You must protect the identity layer

2. **"MFA is necessary but not sufficient"** — 68% bypass rate. Need phishing-resistant MFA (FIDO2), not just push/SMS. Also need continuous session monitoring

3. **"48-minute breakout time"** — automated detection and response is essential. Human SOC analysts can't react fast enough alone

4. **"Cross-domain correlation is critical"** — identity attacks span AD → cloud → SaaS. Single-domain visibility misses the attack chain. CrowdStrike unifies endpoint + identity + cloud telemetry

5. **"AI agent identities are the next frontier"** — superhuman identities with persistent high privileges. Need the same governance as human accounts: least privilege, monitoring, lifecycle management, conditional access

### Common Interview Questions

| Question | Key Talking Points |
|----------|-------------------|
| "How do identity-based attacks work?" | Credential theft (phishing, vishing, access brokers) → initial access → privilege escalation → lateral movement. 79% malware-free per CrowdStrike 2025 GTR |
| "Why does MFA fail?" | AitM proxies steal session tokens, MFA fatigue, SIM swap, help desk social engineering. Solution: FIDO2 (origin-bound, phishing-resistant) |
| "What is ITDR?" | Identity Threat Detection & Response. Real-time monitoring of auth events across AD/Entra/Okta/SaaS. Behavioral analytics detect anomalies. Auto-enforcement (block, re-auth, isolate) |
| "How do you secure AI agents?" | Secretless auth (MI/Federation), least privilege, behavioral monitoring, lifecycle management, conditional access, audit trail |
| "Explain zero trust for identity" | Never trust, always verify. Every request verified (identity + device + context). JIT privileges. Continuous monitoring. Blast radius minimization |

---

## References & Links

### CrowdStrike Reports & Research
- [CrowdStrike 2025 Global Threat Report (PDF)](https://www.securityweek.com/wp-content/uploads/2025/02/CrowdStrikeGlobalThreatReport2025.pdf) — Full annual threat landscape report
- [How to Navigate the 2025 Identity Threat Landscape](https://www.crowdstrike.com/en-us/blog/how-to-navigate-2025-identity-threat-landscape/) — CrowdStrike blog post with identity-specific analysis
- [Falcon Next-Gen Identity Security Press Release](https://www.crowdstrike.com/en-us/press-releases/falcon-next-gen-identity-security-innovations-expand-unified-protection-for-every-identity/) — Official announcement of ITDR, FalconID, PAM innovations
- [NAND Research: CrowdStrike Falcon Next-Gen Identity Security](https://nand-research.com/research-note-crowdstrike-falcon-next-gen-identity-security/) — Independent analyst review

### Industry Analysis
- [CrowdStrike 2025 Threat Hunting Report: Identity Hunting](https://www.itgrcadvisory.com/post/crowdstrike-2025-threat-hunting-report-part-5-identity-hunting) — Deep dive into identity attack detection techniques
- [Cloud Breaches and Identity Hacks Explode](https://siliconangle.com/2025/08/04/cloud-breaches-identity-hacks-explode-crowdstrikes-latest-threat-report/) — SiliconANGLE analysis of H1 2025 cloud/identity trends
- [CrowdStrike Field CTO on Identity-Based Attack Shift](https://cybermagazine.com/articles/crowdstrike-field-cto-warns-of-identity-based-attacks-shift) — Expert perspective on the identity-first security imperative

### Standards & Frameworks
- [FIDO2 / WebAuthn Specification](https://fidoalliance.org/fido2/) — The standard behind phishing-resistant authentication
- [NIST SP 800-63B: Digital Identity Guidelines (Authentication)](https://pages.nist.gov/800-63-3/sp800-63b.html) — US government authentication assurance levels
- [MITRE ATT&CK: Credential Access Tactics](https://attack.mitre.org/tactics/TA0006/) — Comprehensive catalog of credential theft techniques

### Related Guides in This Series
- [Agent Authentication & Identity](appendix-agent-authentication.md) — How agents prove their identity (Managed Identity, Entra Agent ID, Foundry Blueprints, MCP auth)
- [Agent Protocols: MCP, A2A, ACP](appendix-agent-protocols.md) — Protocol-level details of how agents connect to tools and each other
