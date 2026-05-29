# Edge IAM Pattern (Stateless Authentication)

This document outlines how the Merchant-of-Record payment platform handles Authentication and Authorization across a globally distributed "Edge Cell" architecture without introducing a centralized single point of failure or latency bottlenecks.

## The Problem: Centralized IAM Latency
In a globally distributed architecture, "Edge Cells" are deployed close to the end-users (e.g., US-East, EU-West, AP-South) to guarantee ultra-low latency for synchronous checkout requests.

If every Edge Cell was required to validate incoming access tokens by making a synchronous HTTP call to a central Identity and Access Management (IAM) server (like Keycloak) located in a primary datacenter, it would:
1. **Destroy the low-latency guarantees** of the Edge.
2. **Create a Single Point of Failure (SPOF)**. If the central IAM server went down or the cross-region network dropped, all global Edge Cells would instantly fail to process payments.

## The Solution: Decentralized Cryptographic Validation (JWT)

The platform solves this by utilizing **Stateless JSON Web Tokens (JWTs)** and asymmetric cryptography (RS256).

### 1. Zero-Network Validation at the Edge
When the `payment-edge-cell` (The Spring Boot REST API) boots up, it makes **one** initial network call to Keycloak to download the **JSON Web Key Set (JWKS)**. The JWKS contains the public cryptographic keys used by Keycloak to sign tokens.

When a payment request arrives at the Edge Cell:
1. The Edge Cell extracts the JWT from the `Authorization: Bearer <token>` header.
2. Using the cached public key, the Edge Cell uses its own local CPU to cryptographically verify the signature of the token.
3. It locally inspects the claims (e.g., `exp` for expiration, `roles` for authorization).

**Result:** The Edge Cell performs 100% of the authentication and authorization locally. It **never** contacts Keycloak during the critical path of a checkout flow. Even if the entire central datacenter goes offline, the Edge Cells will continue accepting payments as long as the tokens have not expired.

### 2. Client-Side Token Caching (The Checkout Service)
The only actors in the system that actually communicate with Keycloak are the clients (e.g., the Checkout Service or Merchant APIs). 
To prevent hammering the IAM server:
1. The Checkout Service requests a machine-to-machine token (Client Credentials flow) from Keycloak.
2. The token is issued with a strict Time-To-Live (TTL), e.g., 1 hour.
3. The Checkout Service **caches** this token in memory and attaches it to thousands of checkout requests over the next hour.
4. It only makes a network call to Keycloak once an hour to refresh the token.

### 3. Global IAM Replication (Future Scaling)
While the critical path is decoupled from Keycloak, clients still need to acquire tokens. In a massive multi-region deployment, the IAM provider (Keycloak) should be deployed in an Active-Active Cross-Datacenter Replication topology. 

This ensures that a Checkout Service in Asia fetches its tokens from an Asian Keycloak cluster, while the European services fetch from Europe, ensuring token acquisition remains fast and highly available globally.
