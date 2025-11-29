# Payment Checkout Demo

A developer-facing internal demo page for testing payment creation requests without using curl or manually managing JWT tokens.

## Features

- ✅ Interactive form to build payment requests with multiple sellers
- ✅ Automatic JWT token generation and payment processing via backend proxy
- ✅ Real-time validation and total calculation
- ✅ Send POST requests to `/api/v1/payments`
- ✅ Display JSON response and equivalent curl command
- ✅ Error handling and validation

## Prerequisites

- Node.js 18+ and npm/yarn
- Keycloak running and provisioned (see main project docs)
- Payment service API accessible

> ⚠️ **Security Note**: This is a **developer demo tool only**, not for production use. The backend proxy handles all authentication and API calls server-side, keeping the client secret secure (not exposed in browser).

## Setup

1. **Install dependencies:**
   ```bash
   cd checkout-demo
   npm install
   ```

2. **Configure environment:**
   
   **Option A - Automatic (Recommended):**
   
   Run the helper script to automatically generate `.env` from your Keycloak secrets:
   ```bash
   npm run setup-env
   ```
   
   This script will:
   - Read the client secret from `keycloak/output/secrets.txt`
   - Read API endpoints from `infra/endpoints.json` (if available)
   - Generate a `.env` file with all required configuration
   
   **Option B - Manual:**
   
   Create a `.env` file manually:
   ```bash
   cp .env.example .env
   ```
   
   Edit `.env` and set:
   - `VITE_KEYCLOAK_CLIENT_SECRET`: Get this from `keycloak/output/secrets.txt` after running `./keycloak/provision-keycloak.sh`
   
   The other values have sensible defaults:
   - `VITE_KEYCLOAK_URL`: Defaults to `http://127.0.0.1:8080` (assumes port-forwarding)
   - `VITE_API_BASE_URL`: Defaults to `http://127.0.0.1`
   - `VITE_API_HOST_HEADER`: Defaults to `payment.192.168.49.2.nip.io`

3. **Start the development servers:**
   ```bash
   npm run dev
   ```

   This will start both:
   - Frontend server at `http://localhost:3000` (Vite dev server)
   - Backend proxy server at `http://localhost:3001` (simulates order-service/checkout-service)
   
   The app will automatically open at `http://localhost:3000`
   
   **Note**: The backend proxy simulates a production backend service (order-service/checkout-service) that:
   - Gets JWT tokens from Keycloak (server-to-server, no CORS needed)
   - Calls payment-service with the token (server-to-server, no CORS needed)
   - The proxy needs CORS enabled because the browser calls it directly (browser → proxy is cross-origin)

## Usage

1. **Fill Payment Form**:
   - Enter Order ID (e.g., `ORDER-12345`)
   - Enter Buyer ID (e.g., `BUYER-123`)
   - Set total amount (quantity in smallest currency unit, e.g., cents)
   - Select currency
   - Add one or more payment orders with seller ID and amounts
   - The calculated total updates automatically

2. **Send Request**: Click "Send Payment Request". The app will:
   - Validate the form
   - Send request to backend proxy
   - Backend proxy automatically:
     - Gets JWT token from Keycloak (server-to-server)
     - Calls payment-service with the token (server-to-server)
     - Returns the payment result
   - Display the response and equivalent curl command
   
   **Note**: Everything is handled automatically - just fill the form and submit!

## Example Request

```
Order ID: ORDER-20240508-XYZ
Buyer ID: BUYER-123
Total Amount: 19949 (EUR)

Payment Orders:
- SELLER-111: 4999 EUR
- SELLER-222: 2950 EUR  
- SELLER-333: 12000 EUR
```

## Troubleshooting

**Payment Request Errors:**
- Ensure both servers are running (frontend on 3000, proxy on 3001 - check console)
- Ensure Keycloak is running: `kubectl port-forward -n payment svc/keycloak 8080:8080`
- Check that `./keycloak/provision-keycloak.sh` was run
- Verify proxy console shows "Client Secret: ✅ Configured"
- Check proxy console for detailed error messages (it logs token and payment-service call errors)

**API Connection Error:**
- Check that payment service is running and accessible
- The proxy reads from `infra/endpoints.json` automatically (or uses defaults)
- Payment service URL: `http://127.0.0.1/api/v1/payments` with Host header `payment.192.168.49.2.nip.io`
- For Kubernetes: Ensure ingress is set up (see `docs/how-to-start.md` step 5)
- For local access: Port-forwarding may be needed: `kubectl port-forward -n payment svc/payment-service 80:80`
- Check proxy console logs for the exact URL and Host header being used

**Validation Errors:**
- Total amount must equal the sum of all payment orders
- All currencies must match
- All required fields must be filled

## Development

- Built with React 18 + Vite
- Pure JavaScript (no TypeScript per design doc)
- No build step required for development
- Uses Fetch API for HTTP requests

## Production Build

```bash
npm run build
```

Output will be in `dist/` directory.

