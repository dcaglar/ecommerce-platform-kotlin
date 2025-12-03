// Payment API service - Now calls backend proxy (production-like flow)

const PROXY_URL = import.meta.env.VITE_PROXY_URL || 'http://127.0.0.1:3001';

/**
 * Send payment creation request via backend proxy
 * (Simulates production flow where frontend calls backend, backend calls payment-service)
 * Returns full response with payment data and metadata
 */
export async function createPayment(paymentData) {
  const url = `${PROXY_URL}/api/checkout/process-payment`;
  console.log('💳 [PaymentService] Sending payment request...');
  console.log('   URL:', url);
  console.log('   Payload:', JSON.stringify(paymentData, null, 2));
  
  try {
    const startTime = Date.now();
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(paymentData),
    });

    const duration = Date.now() - startTime;
    console.log(`   Response received (${duration}ms):`, response.status, response.statusText);

    let responseData;
    try {
      responseData = await response.json();
      console.log('   Response body:', JSON.stringify(responseData, null, 2));
    } catch (parseError) {
      console.error('   Failed to parse response as JSON:', parseError);
      responseData = {};
    }

    if (!response.ok) {
      console.error('   Request failed:', response.status, response.statusText);
      console.error('   Error details:', responseData);
      throw {
        status: response.status,
        statusText: response.statusText,
        data: responseData,
        message: responseData.message || responseData.error || `HTTP ${response.status}: ${response.statusText}`,
      };
    }

    console.log('✅ [PaymentService] Payment request successful');
    // Return full response (includes payment and metadata)
    return responseData;
  } catch (error) {
    if (error.status) {
      // Already formatted error from above
      throw error;
    }
    // Network or other error
    console.error('❌ [PaymentService] Network error:', error);
    throw {
      status: 0,
      statusText: 'Network Error',
      data: {},
      message: error.message || 'Failed to connect to proxy server',
    };
  }
}

/**
 * Generate curl command equivalent
 * Can generate either:
 * - Proxy endpoint curl (if no token/metadata provided)
 * - Direct payment-service curl (if token/metadata from proxy response)
 */
export function generateCurlCommand(paymentData, metadata = null) {
  const jsonBody = JSON.stringify(paymentData, null, 2);
  const escapedJson = jsonBody.replace(/'/g, "'\\''");
  
  // If we have metadata from proxy (with token), show direct payment-service curl
  if (metadata && metadata.token) {
    const url = metadata.usedApiUrl || import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1';
    const host = metadata.usedHostHeader || import.meta.env.VITE_API_HOST_HEADER || 'payment.192.168.49.2.nip.io';
    const idempotencyKey = metadata.idempotencyKey || 'YOUR-IDEMPOTENCY-KEY';
    return `curl -i -X POST "${url}/api/v1/payments" \\\n  -H "Host: ${host}" \\\n  -H "Content-Type: application/json" \\\n  -H "Authorization: Bearer ${metadata.token}" \\\n  -H "Idempotency-Key: ${idempotencyKey}" \\\n  -d '${escapedJson}'`;
  }
  
  // Otherwise, show proxy endpoint curl (simpler, production-like)
  const proxyUrl = import.meta.env.VITE_PROXY_URL || 'http://127.0.0.1:3001';
  return `curl -i -X POST "${proxyUrl}/api/checkout/process-payment" \\\n  -H "Content-Type: application/json" \\\n  -d '${escapedJson}'`;
}

