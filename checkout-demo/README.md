# Payment Checkout Demo

A developer-friendly web interface for testing the complete payment flow with Stripe Payment Element integration.

## Overview

This demo provides an interactive UI to test payment creation and authorization without manually managing JWT tokens or using curl commands. It includes:

- ✅ Interactive form for building payment requests
- ✅ Automatic JWT token acquisition via backend proxy
- ✅ Stripe Payment Element integration for secure card collection
- ✅ Automatic idempotency key generation
- ✅ Real-time validation and error handling

> ⚠️ **Security Note**: This is a developer demo tool only, not for production use. The backend proxy handles authentication server-side, keeping client secrets secure.

## Prerequisites

- Node.js 18+ and npm
- Infrastructure deployed (see `docs/how-to-start.md` steps 1-8)
- Keycloak provisioned (see `docs/how-to-start.md` step 8)
- Stripe account with test API keys

## Quick Start

### 1. Install Dependencies

```bash
cd checkout-demo
npm install
```

### 2. Generate Environment Configuration

Run the setup script to generate `.env` file:

```bash
npm run setup-env
```

This script:
- Reads Keycloak client secret from `../keycloak/output/secrets.txt`
- Reads API endpoints from `../infra/endpoints.json`
- Generates `.env` with all required configuration

### 3. Configure Stripe Publishable Key

**Get your Stripe publishable key:**
1. Go to https://dashboard.stripe.com/test/apikeys
2. Copy your **Publishable key** (starts with `pk_test_`)

**Add it to `.env`:**

Edit `checkout-demo/.env` and replace the placeholder:

```bash
# Find this line in .env:
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_placeholder

# Replace with your actual key:
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_YOUR_ACTUAL_KEY_HERE
```

> **Important**: The publishable key is different from the secret key. The publishable key is safe to expose in the browser and is required for Stripe Elements.

### 4. Start the Demo

```bash
npm run dev
```

This starts:
- **Frontend**: `http://localhost:3000` (Vite dev server)
- **Backend Proxy**: `http://localhost:3001` (Express server)

The app will automatically open in your browser.

## Usage

### Creating a Payment

1. **Fill Order Details:**
   - Order ID (e.g., `ORDER-TEST-001`)
   - Buyer ID (e.g., `BUYER-123`)
   - Total amount in smallest currency unit (e.g., cents: `2900` for €29.00)
   - Select currency (EUR, USD, GBP)
   - Add payment orders for each seller:
     - Seller ID (e.g., `SELLER-111`)
     - Amount (e.g., `1450` for €14.50)

2. **Click "Proceed to Checkout":**
   - Frontend validates the form
   - Backend proxy automatically:
     - Gets JWT token from Keycloak
     - Generates idempotency key
     - Creates payment intent via payment-service API
     - Returns `paymentIntentId` and `clientSecret`

3. **Enter Payment Details:**
   - Stripe Payment Element is initialized with the `clientSecret`
   - Enter card details in the secure Stripe form
   - Card data goes directly to Stripe (never touches your servers)
   - Click "Pay Now"

4. **Authorize Payment:**
   - Frontend calls authorize endpoint
   - Backend confirms payment with Stripe
   - Success or error message is displayed

### Example Payment Request

```
Order ID: ORDER-TEST-001
Buyer ID: BUYER-123
Total Amount: 2900 (cents)
Currency: EUR

Payment Orders:
- SELLER-111: 1450 EUR
- SELLER-222: 1450 EUR
```

## Architecture

### Components

- **Frontend (React)**: React 18 + Vite, uses Stripe.js and Payment Element
- **Backend Proxy (Node.js)**: Express server that simulates order-service/checkout-service

### Payment Flow

```
┌─────────────┐
│   Browser   │
│  (React)    │
└──────┬──────┘
       │ 1. Create Payment Request
       ▼
┌─────────────────┐
│  Backend Proxy  │
│  (Node.js)      │
└──────┬──────────┘
       │ 2. Get JWT Token
       ▼
┌─────────────┐
│  Keycloak   │
└──────┬──────┘
       │ 3. Return Token
       ▼
┌─────────────────┐      ┌──────────────┐
│  Backend Proxy  │──────▶│ Payment      │
└──────┬──────────┘      │ Service      │
       │ 4. Create Payment│              │
       │    (with token)  └──────────────┘
       │
       │ 5. Return clientSecret
       ▼
┌─────────────┐
│   Browser   │
│  (React)    │
└──────┬──────┘
       │ 6. Initialize Stripe Payment Element
       │    with clientSecret
       ▼
┌─────────────┐
│   Stripe    │
│  (Direct)   │
└─────────────┘
       │ 7. Card data sent directly to Stripe
       │    (never touches your servers)
       │
       │ 8. Authorize Payment
       ▼
┌─────────────────┐      ┌──────────────┐
│  Backend Proxy  │──────▶│ Payment      │
└──────┬──────────┘      │ Service      │
       │                  └──────────────┘
       │ 9. Confirm with Stripe
       ▼
┌─────────────┐
│   Browser   │
│  (React)    │
└─────────────┘
```

### Key Points

- **Card Data**: Never touches your servers - goes directly from browser to Stripe
- **Backend Proxy**: Handles all authentication (Keycloak token acquisition)
- **Idempotency**: Automatically generated UUID for each payment request
- **CORS**: Enabled on backend proxy because browser calls it directly

## Configuration

### Environment Variables

The `.env` file contains:

```bash
# Keycloak Configuration
VITE_KEYCLOAK_URL=http://127.0.0.1:8080
VITE_KEYCLOAK_REALM=ecommerce-platform
VITE_KEYCLOAK_CLIENT_ID=payment-service
VITE_KEYCLOAK_CLIENT_SECRET=<auto-generated>

# Payment API Configuration
VITE_API_BASE_URL=http://127.0.0.1
VITE_API_HOST_HEADER=payment.192.168.49.2.nip.io

# Stripe Configuration
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_YOUR_KEY_HERE
```

### Default Values

Most values are auto-generated by `npm run setup-env`. Defaults:

- `VITE_KEYCLOAK_URL`: `http://127.0.0.1:8080` (assumes port-forwarding)
- `VITE_API_BASE_URL`: `http://127.0.0.1`
- `VITE_API_HOST_HEADER`: `payment.192.168.49.2.nip.io`

## Troubleshooting

### "Client secret not configured"

**Problem**: Backend proxy cannot authenticate with Keycloak.

**Solution**:
1. Ensure Keycloak is provisioned: `./keycloak/provision-keycloak.sh`
2. Run `npm run setup-env` to regenerate `.env` file
3. Verify `VITE_KEYCLOAK_CLIENT_SECRET` is set in `.env`

### "Cannot reach Keycloak"

**Problem**: Backend proxy cannot connect to Keycloak.

**Solution**:
1. Start port-forwarding: `kubectl port-forward -n payment svc/keycloak 8080:8080`
2. Verify Keycloak is accessible: `curl http://127.0.0.1:8080/health`
3. Check `.env` has correct `VITE_KEYCLOAK_URL`

### Stripe 401 Error

**Problem**: Stripe API returns 401 Unauthorized.

**Solution**:
1. Verify `VITE_STRIPE_PUBLISHABLE_KEY` in `.env` is your actual Stripe key (not `pk_test_placeholder`)
2. Get your key from: https://dashboard.stripe.com/test/apikeys
3. Ensure key starts with `pk_test_` (test mode) or `pk_live_` (live mode)
4. Restart dev server after updating `.env`: `npm run dev`

### Payment Request Errors

**Problem**: Payment creation fails.

**Solution**:
1. Check backend proxy console (terminal where `npm run dev` is running)
2. Verify payment service is running: `kubectl get pods -n payment`
3. Check `infra/endpoints.json` exists and has correct endpoints
4. Verify Keycloak token is valid (check proxy console logs)

### Network Errors

**Problem**: Cannot connect to payment service.

**Solution**:
1. Verify payment service ingress is set up (see `docs/how-to-start.md` step 5)
2. Check `infra/endpoints.json` has correct `base_url` and `host_header`
3. For local access, ensure port-forwarding is active if needed

### Validation Errors

**Problem**: Form validation fails.

**Solution**:
- Total amount must equal sum of all payment orders
- All currencies must match
- All required fields must be filled
- Amounts must be positive integers

## Development

### Tech Stack

- **Frontend**: React 18, Vite, Stripe.js, Stripe React Components
- **Backend**: Node.js, Express
- **Language**: JavaScript (no TypeScript)

### Scripts

```bash
# Start development servers (frontend + backend proxy)
npm run dev

# Build for production
npm run build

# Generate .env file from Keycloak secrets
npm run setup-env

# Preview production build
npm run preview
```

### Project Structure

```
checkout-demo/
├── src/
│   ├── App.jsx              # Main application component
│   ├── components/
│   │   └── PaymentElement.jsx  # Stripe Payment Element wrapper
│   ├── services/
│   │   └── paymentService.js   # API client for payment endpoints
│   └── main.jsx             # React entry point
├── server.js                # Backend proxy server
├── generate-env.cjs         # Environment configuration generator
├── .env                     # Environment variables (generated)
└── package.json
```

## Production Build

Build the frontend for production:

```bash
npm run build
```

Output will be in `dist/` directory. Serve with any static file server:

```bash
# Using Python
python -m http.server 3000 -d dist

# Using Node.js
npx serve dist -p 3000
```

> **Note**: The backend proxy (`server.js`) must also be running in production for the demo to work.
