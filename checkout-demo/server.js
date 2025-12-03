#!/usr/bin/env node

/**
 * Backend proxy server that simulates order-service/checkout-service
 * - Handles Keycloak token acquisition (server-to-server)
 * - Calls payment-service with token (server-to-server)
 * - Returns response to frontend
 * This simulates production flow while avoiding CORS issues
 */

import express from 'express';
import cors from 'cors';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import http from 'http';
import https from 'https';
import { URL } from 'url';
import { randomUUID } from 'crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3001;

// Middleware
app.use(cors());
app.use(express.json());

// Helper to read .env file values
function readEnvFile() {
  const env = {};
  try {
    const envPath = path.join(__dirname, '.env');
    if (fs.existsSync(envPath)) {
      const envContent = fs.readFileSync(envPath, 'utf8');
      for (const line of envContent.split('\n')) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const match = trimmed.match(/^([^=]+)=(.*)$/);
        if (match) {
          const key = match[1].trim();
          const value = match[2].trim();
          // Remove VITE_ prefix if present (for compatibility)
          const envKey = key.replace(/^VITE_/, '');
          env[envKey] = value;
        }
      }
    }
  } catch (error) {
    console.warn('Could not read .env file:', error.message);
  }
  return env;
}

const envFile = readEnvFile();

// Read configuration from environment variables, .env file, or defaults
const KEYCLOAK_URL = process.env.KEYCLOAK_URL || envFile.KEYCLOAK_URL || 'http://127.0.0.1:8080';
const REALM = process.env.KEYCLOAK_REALM || envFile.KEYCLOAK_REALM || 'ecommerce-platform';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID || envFile.KEYCLOAK_CLIENT_ID || 'payment-service';

// Try to read client secret from environment, .env file, or secrets.txt
function getClientSecret() {
  // First, try environment variable
  if (process.env.KEYCLOAK_CLIENT_SECRET) {
    return process.env.KEYCLOAK_CLIENT_SECRET;
  }

  // Try reading from already-parsed .env file (VITE_ prefix is already stripped)
  if (envFile.KEYCLOAK_CLIENT_SECRET) {
    return envFile.KEYCLOAK_CLIENT_SECRET;
  }

  // Try reading from secrets.txt as fallback
  try {
    const secretsPath = path.join(__dirname, '..', 'keycloak', 'output', 'secrets.txt');
    if (fs.existsSync(secretsPath)) {
      const secretsContent = fs.readFileSync(secretsPath, 'utf8');
      const match = secretsContent.match(/PAYMENT_SERVICE_CLIENT_SECRET=(.+)/);
      if (match) {
        return match[1].trim();
      }
    }
  } catch (error) {
    console.warn('Could not read secrets.txt:', error.message);
  }

  return null;
}

const CLIENT_SECRET = getClientSecret();

// Helper to read endpoints.json (like generate-env.cjs does)
function readEndpoints() {
  try {
    const endpointsPath = path.join(__dirname, '..', 'infra', 'endpoints.json');
    if (fs.existsSync(endpointsPath)) {
      const content = fs.readFileSync(endpointsPath, 'utf8');
      return JSON.parse(content);
    }
  } catch (error) {
    console.warn('Could not read infra/endpoints.json:', error.message);
  }
  return null;
}

// Payment API configuration (read from env, .env file, endpoints.json, or defaults)
// Priority: env var > .env file > endpoints.json > defaults
const endpoints = readEndpoints();
const PAYMENT_API_BASE_URL = process.env.PAYMENT_API_BASE_URL || 
                              envFile.API_BASE_URL || 
                              (endpoints && endpoints.base_url) || 
                              'http://127.0.0.1';
const PAYMENT_API_HOST_HEADER = process.env.PAYMENT_API_HOST_HEADER || 
                                 envFile.API_HOST_HEADER || 
                                 (endpoints && endpoints.host_header) || 
                                 'payment.192.168.49.2.nip.io';

// Helper function to get access token from Keycloak
async function getAccessToken() {
  if (!CLIENT_SECRET) {
    throw new Error('Client secret not configured');
  }

  const tokenEndpoint = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
  console.log(`      🔑 Token endpoint: ${tokenEndpoint}`);
  
  const formData = new URLSearchParams();
  formData.append('grant_type', 'client_credentials');
  formData.append('client_id', CLIENT_ID);
  formData.append('client_secret', CLIENT_SECRET);

  let response;
  try {
    response = await fetch(tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: formData,
    });
    console.log(`      📡 Keycloak response: ${response.status} ${response.statusText}`);
  } catch (fetchError) {
    console.error(`      ❌ Network error calling Keycloak:`, fetchError.message);
    throw new Error(`Cannot reach Keycloak at ${KEYCLOAK_URL}: ${fetchError.message}`);
  }

  let responseData;
  try {
    responseData = await response.json();
  } catch (parseError) {
    console.error(`      ❌ Failed to parse Keycloak response:`, parseError.message);
    const text = await response.text().catch(() => 'Unable to read response body');
    throw new Error(`Invalid JSON response from Keycloak: ${text.substring(0, 200)}`);
  }

  if (!response.ok) {
    console.error(`      ❌ Keycloak error response:`, JSON.stringify(responseData, null, 2));
    throw new Error(`Keycloak token request failed: ${JSON.stringify(responseData)}`);
  }

  if (!responseData.access_token) {
    console.error(`      ❌ No access_token in response:`, JSON.stringify(responseData, null, 2));
    throw new Error('No access token in response');
  }

  return responseData.access_token;
}

// Helper function to make HTTP requests with custom Host header (for ingress routing)
function httpRequestWithHost(url, options = {}) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const { method = 'GET', headers = {}, body, hostHeader, timeout = 30000 } = options;
    
    const requestOptions = {
      hostname: urlObj.hostname,
      port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
      path: urlObj.pathname + urlObj.search,
      method: method,
      headers: {
        ...headers,
        // Override Host header if provided (for ingress routing)
        ...(hostHeader && { Host: hostHeader }),
      },
      timeout: timeout,
    };

    // Use https module for HTTPS requests, http module for HTTP
    const protocol = urlObj.protocol === 'https:' ? https : http;
    const req = protocol.request(requestOptions, (res) => {
      let data = '';
      
      res.on('data', (chunk) => {
        data += chunk;
      });
      
      res.on('end', () => {
        // Create a Response-like object
        resolve({
          ok: res.statusCode >= 200 && res.statusCode < 300,
          status: res.statusCode,
          statusText: res.statusMessage,
          headers: res.headers,
          json: async () => {
            try {
              return JSON.parse(data);
            } catch (e) {
              throw new Error(`Failed to parse JSON: ${data.substring(0, 200)}`);
            }
          },
          text: async () => data,
        });
      });
    });

    req.on('error', (error) => {
      reject(error);
    });

    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request timeout'));
    });

    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    
    req.end();
  });
}

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Token endpoint (kept for backwards compatibility or direct token needs)
app.post('/api/token', async (req, res) => {
  try {
    if (!CLIENT_SECRET) {
      return res.status(500).json({
        error: 'Client secret not configured',
        message: 'Please set KEYCLOAK_CLIENT_SECRET environment variable or run npm run setup-env'
      });
    }

    const token = await getAccessToken();
    
    res.json({
      access_token: token,
      token_type: 'Bearer',
      expires_in: 3600 // Default, could be extracted from Keycloak response
    });

  } catch (error) {
    console.error('Token proxy error:', error);
    res.status(500).json({
      error: 'Failed to get token',
      message: error.message,
      details: error.cause || 'Unknown error'
    });
  }
});

// Checkout endpoint: Simulates order-service/checkout-service
// Handles full flow: token acquisition + payment-service call
app.post('/api/checkout/process-payment', async (req, res) => {
  const requestId = Date.now().toString(36);
  console.log(`\n📥 [${requestId}] Received payment request: ${req.method} ${req.url}`);
  console.log(`   [${requestId}] Request body:`, JSON.stringify(req.body, null, 2));
  
  try {
    if (!CLIENT_SECRET) {
      console.error(`   [${requestId}] ❌ Client secret not configured`);
      return res.status(500).json({
        error: 'Client secret not configured',
        message: 'Please set KEYCLOAK_CLIENT_SECRET environment variable or run npm run setup-env'
      });
    }

    const paymentData = req.body;
    console.log(`   [${requestId}] ✅ Client secret configured`);

    // Validate payment data
    console.log(`   [${requestId}] 🔍 Validating payment data...`);
    if (!paymentData || !paymentData.orderId || !paymentData.buyerId) {
      console.error(`   [${requestId}] ❌ Validation failed: Missing required fields`);
      console.error(`   [${requestId}]    Payment data:`, JSON.stringify(paymentData, null, 2));
      return res.status(400).json({
        error: 'Invalid payment data',
        message: 'Missing required fields: orderId, buyerId'
      });
    }
    console.log(`   [${requestId}] ✅ Validation passed: orderId=${paymentData.orderId}, buyerId=${paymentData.buyerId}`);

    // Step 1: Get token from Keycloak (server-to-server)
    console.log(`   [${requestId}] 🔐 Step 1: Acquiring token from Keycloak...`);
    console.log(`   [${requestId}]    Keycloak URL: ${KEYCLOAK_URL}`);
    console.log(`   [${requestId}]    Realm: ${REALM}`);
    console.log(`   [${requestId}]    Client ID: ${CLIENT_ID}`);
    
    let token;
    try {
      const tokenStartTime = Date.now();
      token = await getAccessToken();
      const tokenDuration = Date.now() - tokenStartTime;
      console.log(`   [${requestId}] ✅ Token acquired successfully (${tokenDuration}ms)`);
      console.log(`   [${requestId}]    Token preview: ${token.substring(0, 20)}...`);
    } catch (error) {
      console.error(`   [${requestId}] ❌ Token acquisition failed:`, error.message);
      console.error(`   [${requestId}]    Error details:`, error);
      return res.status(500).json({
        error: 'Failed to get authentication token',
        message: error.message
      });
    }

    // Step 2: Generate idempotency key for this request
    // In production, this would be provided by the client, but for demo we generate it
    const idempotencyKey = randomUUID();
    console.log(`   [${requestId}] 🔑 Generated idempotency key: ${idempotencyKey}`);

    // Step 3: Call payment-service with token (server-to-server)
    // Access via ingress: base URL + Host header (matches how-to-start.md)
    console.log(`   [${requestId}] 💳 Step 3: Calling payment-service...`);
    const paymentUrl = `${PAYMENT_API_BASE_URL}/api/v1/payments`;
    console.log(`   [${requestId}]    URL: ${paymentUrl}`);
    console.log(`   [${requestId}]    Host header: ${PAYMENT_API_HOST_HEADER}`);
    console.log(`   [${requestId}]    Idempotency-Key: ${idempotencyKey}`);
    console.log(`   [${requestId}]    Request payload:`, JSON.stringify(paymentData, null, 2));
    
    const paymentStartTime = Date.now();
    let paymentResponse;
    try {
      console.log(`   [${requestId}]    🔄 Sending request to payment-service...`);
      console.log(`   [${requestId}]    Full request details:`);
      console.log(`   [${requestId}]      Method: POST`);
      console.log(`   [${requestId}]      URL: ${paymentUrl}`);
      console.log(`   [${requestId}]      Host header: ${PAYMENT_API_HOST_HEADER}`);
      console.log(`   [${requestId}]      Idempotency-Key: ${idempotencyKey}`);
      console.log(`   [${requestId}]      Token length: ${token.length} chars`);
      
      // Use custom httpRequestWithHost to properly set Host header for ingress routing
      // Node.js fetch() doesn't allow overriding Host header, so we use native http module
      paymentResponse = await httpRequestWithHost(paymentUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'Idempotency-Key': idempotencyKey,
        },
        hostHeader: PAYMENT_API_HOST_HEADER, // Custom Host header for ingress routing
        body: JSON.stringify(paymentData),
        timeout: 30000,
      });
      
      const paymentDuration = Date.now() - paymentStartTime;
      console.log(`   [${requestId}] 📡 Payment-service response received (${paymentDuration}ms)`);
      console.log(`   [${requestId}]    Status: ${paymentResponse.status} ${paymentResponse.statusText}`);
      console.log(`   [${requestId}]    Response headers:`, paymentResponse.headers);
      
    } catch (fetchError) {
      const paymentDuration = Date.now() - paymentStartTime;
      if (fetchError.message === 'Request timeout') {
        console.error(`   [${requestId}] ❌ Request timeout after ${paymentDuration}ms (30s limit)`);
        console.error(`   [${requestId}]    Payment service did not respond in time`);
        return res.status(504).json({
          error: 'Payment service timeout',
          message: 'Payment service did not respond within 30 seconds',
          details: 'Check if payment service is running and accessible at ' + paymentUrl
        });
      }
      console.error(`   [${requestId}] ❌ Network error calling payment-service (after ${paymentDuration}ms):`, fetchError.message);
      console.error(`   [${requestId}]    Error name:`, fetchError.name);
      console.error(`   [${requestId}]    Error stack:`, fetchError.stack);
      console.error(`   [${requestId}]    Full error:`, fetchError);
      return res.status(502).json({
        error: 'Network error calling payment service',
        message: fetchError.message,
        details: `Unable to reach payment service at ${paymentUrl}. Check if it's running and accessible.`
      });
    }

    let paymentResponseData;
    try {
      paymentResponseData = await paymentResponse.json();
      console.log(`   [${requestId}] 📄 Response body parsed successfully`);
    } catch (parseError) {
      console.error(`   [${requestId}] ⚠️  Failed to parse response as JSON:`, parseError.message);
      // Try to get text response for debugging
      try {
        const textResponse = await paymentResponse.text();
        console.error(`   [${requestId}]    Response text (first 500 chars):`, textResponse.substring(0, 500));
      } catch (textError) {
        console.error(`   [${requestId}]    Could not read response text:`, textError.message);
      }
      paymentResponseData = {};
    }

    if (!paymentResponse.ok) {
      console.error(`   [${requestId}] ❌ Payment service returned error:`);
      console.error(`   [${requestId}]    Status: ${paymentResponse.status} ${paymentResponse.statusText}`);
      console.error(`   [${requestId}]    Response:`, JSON.stringify(paymentResponseData, null, 2));
      return res.status(paymentResponse.status).json({
        error: 'Payment service request failed',
        status: paymentResponse.status,
        statusText: paymentResponse.statusText,
        details: paymentResponseData
      });
    }
    
    console.log(`   [${requestId}] ✅ Payment created successfully`);
    console.log(`   [${requestId}]    Response:`, JSON.stringify(paymentResponseData, null, 2));

    // Step 4: Return payment response to frontend (with metadata for curl generation)
    console.log(`   [${requestId}] 📤 Returning response to frontend`);
    const response = {
      success: true,
      payment: paymentResponseData,
      // Include metadata for curl command generation
      _meta: {
        token: token,
        idempotencyKey: idempotencyKey,
        usedApiUrl: paymentUrl,
        usedHostHeader: PAYMENT_API_HOST_HEADER
      }
    };
    res.json(response);
    console.log(`   [${requestId}] ✅ Request completed successfully\n`);

  } catch (error) {
    console.error(`   [${requestId}] ❌ Unexpected error in payment processing:`, error.message);
    console.error(`   [${requestId}]    Stack trace:`, error.stack);
    res.status(500).json({
      error: 'Failed to process payment',
      message: error.message,
      details: error.cause || 'Unknown error'
    });
    console.log(`   [${requestId}] ❌ Request failed\n`);
  }
});

// 404 handler for undefined routes
app.use((req, res) => {
  console.error(`❌ Route not found: ${req.method} ${req.url}`);
  res.status(404).json({
    error: 'Route not found',
    method: req.method,
    path: req.url,
    availableRoutes: [
      'GET /health',
      'POST /api/token',
      'POST /api/checkout/process-payment'
    ]
  });
});

app.listen(PORT, () => {
  console.log(`🚀 Backend proxy server running on http://localhost:${PORT}`);
  console.log(`   (Simulates order-service/checkout-service)`);
  console.log(`   Keycloak URL: ${KEYCLOAK_URL}`);
  console.log(`   Realm: ${REALM}`);
  console.log(`   Client ID: ${CLIENT_ID}`);
  console.log(`   Client Secret: ${CLIENT_SECRET ? '✅ Configured' : '❌ Not found'}`);
  console.log(`   Payment API URL: ${PAYMENT_API_BASE_URL}`);
  console.log(`   Payment API Host Header: ${PAYMENT_API_HOST_HEADER}`);
  console.log(`   Payment Service Endpoint: ${PAYMENT_API_BASE_URL}/api/v1/payments`);
  console.log(`\n📋 Available endpoints:`);
  console.log(`   GET  /health`);
  console.log(`   POST /api/token`);
  console.log(`   POST /api/checkout/process-payment`);
  if (!CLIENT_SECRET) {
    console.log(`\n   ⚠️  Please set KEYCLOAK_CLIENT_SECRET or run: npm run setup-env`);
  }
  if (PAYMENT_API_BASE_URL === 'http://127.0.0.1' && !endpoints) {
    console.log(`\n   💡 Tip: Payment service may need port-forwarding if running in Kubernetes`);
    console.log(`   Run: kubectl port-forward -n payment svc/payment-service 80:80`);
  }
});

