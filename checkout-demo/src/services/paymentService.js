// Payment API service - Handles payment creation and authorization

const PROXY_URL = import.meta.env.VITE_PROXY_URL || 'http://127.0.0.1:3001';

/**
 * Send payment creation request via backend proxy
 * Returns payment response with paymentIntentId and clientSecret
 */
export async function createPayment(paymentData) {
  const url = `${PROXY_URL}/api/checkout/process-payment`;
  console.log('💳 [PaymentService] Creating payment...');
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

    console.log('✅ [PaymentService] Payment created successfully');
    // Return payment data from response
    return {
      payment: responseData.payment || responseData,
      status: response.status, // 201, 200, or 202
      metadata: responseData._meta || null
    };
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
 * Poll payment status endpoint to check if client secret is available
 * Used when payment creation returns 202 (pending)
 */
export async function pollPaymentStatus(paymentIntentId, maxAttempts = 30, intervalMs = 2000) {
  const url = `${PROXY_URL}/api/checkout/payment-status/${paymentIntentId}`;
  console.log('🔄 [PaymentService] Polling payment status...');
  console.log('   URL:', url);
  console.log('   Max attempts:', maxAttempts, 'Interval:', intervalMs, 'ms');
  
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      console.log(`   Attempt ${attempt}/${maxAttempts}...`);
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const data = await response.json();
        console.log(`   ✅ Payment status retrieved:`, data);
        
        // If client secret is available, return the payment data
        if (data.payment && data.payment.clientSecret) {
          console.log('   ✅ Client secret is now available!');
          return {
            payment: data.payment,
            status: 200,
            metadata: data._meta || null
          };
        }
        
        // If still pending, continue polling
        if (attempt < maxAttempts) {
          console.log(`   ⏳ Still pending, waiting ${intervalMs}ms before next attempt...`);
          await new Promise(resolve => setTimeout(resolve, intervalMs));
        }
      } else {
        console.warn(`   ⚠️  Status check failed: ${response.status} ${response.statusText}`);
        if (attempt < maxAttempts) {
          await new Promise(resolve => setTimeout(resolve, intervalMs));
        }
      }
    } catch (error) {
      console.error(`   ❌ Error polling status (attempt ${attempt}):`, error);
      if (attempt < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, intervalMs));
      }
    }
  }
  
  // If we exhausted all attempts, throw an error
  throw {
    status: 0,
    statusText: 'Polling Timeout',
    data: {},
    message: 'Payment status polling timed out. Client secret not available after maximum attempts.',
  };
}

/**
 * Authorize a payment intent
 * Called after Stripe Payment Element has collected payment details
 * No payment details are sent - backend uses stored PaymentIntent ID
 * @param {string} paymentIntentId - The internal payment intent ID
 */
export async function authorizePayment(paymentIntentId) {
  const url = `${PROXY_URL}/api/checkout/authorize-payment/${paymentIntentId}`;
  console.log('🔐 [PaymentService] Authorizing payment...');
  console.log('   URL:', url);
  console.log('   Payment Intent ID:', paymentIntentId);
  console.log('   Note: No payment details sent - backend uses stored PaymentIntent ID');
  
  try {
    const startTime = Date.now();
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      // No payment details sent - backend looks up payment and uses stored PaymentIntent ID
      // Payment method is already attached to PaymentIntent by Stripe Payment Element
      body: JSON.stringify({}),
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
      console.error('   Authorization failed:', response.status, response.statusText);
      console.error('   Error details:', responseData);
      throw {
        status: response.status,
        statusText: response.statusText,
        data: responseData,
        message: responseData.message || responseData.error || `HTTP ${response.status}: ${response.statusText}`,
      };
    }

    console.log('✅ [PaymentService] Payment authorized successfully');
    return {
      payment: responseData.payment || responseData,
      status: response.status,
      metadata: responseData._meta || null
    };
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
