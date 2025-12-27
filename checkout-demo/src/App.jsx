import { useState, useEffect, useRef } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { Elements } from '@stripe/react-stripe-js';
import { PaymentForm } from './components/PaymentElement';
import { createPayment, pollPaymentStatus, authorizePayment } from './services/paymentService';
import './index.css';

const stripePromise = loadStripe(
  import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY || 'pk_test_placeholder'
);

const STEPS = {
  FORM: 'form',
  CREATING: 'creating',
  PAYMENT: 'payment',
  AUTHORIZING: 'authorizing',
  SUCCESS: 'success',
  ERROR: 'error'
};

function App() {
  // Form state
  const [orderId, setOrderId] = useState('1700');
  const [buyerId, setBuyerId] = useState('1700');
  const [totalAmount, setTotalAmount] = useState('1700');
  const [currency, setCurrency] = useState('EUR');
  const [paymentOrders, setPaymentOrders] = useState([
    { id: 1, sellerId: 'SELLER-111', amount: '1700' }
  ]);

  // Payment flow state
  const [step, setStep] = useState(STEPS.FORM);
  const [paymentData, setPaymentData] = useState(null);
  const [clientSecret, setClientSecret] = useState(null);
  const [paymentIntentId, setPaymentIntentId] = useState(null);
  const [error, setError] = useState(null);
  const [retryCountdown, setRetryCountdown] = useState(null);
  const retryTimeoutRef = useRef(null);
  const retryIntervalRef = useRef(null);

  // Payment order management
  const addPaymentOrder = () => {
    const newId = Math.max(...paymentOrders.map(po => po.id), 0) + 1;
    setPaymentOrders([...paymentOrders, { id: newId, sellerId: '', amount: '' }]);
  };

  const removePaymentOrder = (id) => {
    if (paymentOrders.length > 1) {
      setPaymentOrders(paymentOrders.filter(po => po.id !== id));
    }
  };

  const updatePaymentOrder = (id, field, value) => {
    setPaymentOrders(paymentOrders.map(po => 
      po.id === id ? { ...po, [field]: value } : po
    ));
  };

  const totalCalculated = paymentOrders.reduce((sum, po) => sum + (parseInt(po.amount) || 0), 0);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => {
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
      }
      if (retryIntervalRef.current) {
        clearInterval(retryIntervalRef.current);
      }
    };
  }, []);

  // ============================================
  // Payment Flow Handlers
  // ============================================

  /**
   * Handles 201 CREATED / 200 OK responses
   * Uses response body content to determine action (same HTTP status can have different meanings)
   */
  const handlePaymentCreated = async (payment) => {
    // Case 1: Payment created successfully with clientSecret
    if (payment.clientSecret && payment.paymentIntentId) {
      setClientSecret(payment.clientSecret);
      setPaymentIntentId(payment.paymentIntentId);
      setStep(STEPS.PAYMENT);
      return;
    }

    // Case 2: Payment declined
    if (payment.status === 'DECLINED') {
      setError('Payment was declined by the payment provider. Please try a different payment method.');
      setStep(STEPS.ERROR);
      return;
    }

    // Case 3: Payment pending (can happen with 200 REPLAYED when original was 202)
    if (payment.status === 'CREATED_PENDING' && payment.paymentIntentId) {
      await handlePaymentAccepted(payment);
      return;
    }

    // Case 4: Unexpected state
    setError('Payment created but client secret is not available. Please try again.');
    setStep(STEPS.ERROR);
  };

  /**
   * Handles 202 ACCEPTED - payment is processing asynchronously
   * Polls the status endpoint until clientSecret is available
   */
  const handlePaymentAccepted = async (payment) => {
    if (!payment.paymentIntentId) {
      setError('Payment ID missing');
      setStep(STEPS.ERROR);
      return;
    }

    setPaymentIntentId(payment.paymentIntentId);
    
    try {
      const pollResult = await pollPaymentStatus(payment.paymentIntentId);
      if (pollResult.payment?.clientSecret) {
        setClientSecret(pollResult.payment.clientSecret);
        setPaymentIntentId(pollResult.payment.paymentIntentId || payment.paymentIntentId);
        setStep(STEPS.PAYMENT);
      } else {
        throw new Error('Client secret not available after polling. Payment may still be processing.');
      }
    } catch (pollErr) {
      setError(getErrorMessage(pollErr) || 'Failed to poll payment status');
      setStep(STEPS.ERROR);
    }
  };

  /**
   * Handles 409 CONFLICT with Retry-After header (temporary conflict)
   * Retries the createPayment call after the delay
   * Note: Uses same idempotency key for retry (idempotency purpose)
   */
  const handleTemporaryConflict = (retryAfterSeconds, paymentRequest) => {
    // Ensure idempotency key exists (should already be set, but fallback for safety)
    if (!paymentRequest._idempotencyKey) {
      paymentRequest._idempotencyKey = crypto.randomUUID();
    }
    setRetryCountdown(retryAfterSeconds);
    setError(`Payment is still being processed. Retrying in ${retryAfterSeconds} seconds...`);
    
    // Clear any existing retry
    if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
    if (retryIntervalRef.current) clearInterval(retryIntervalRef.current);

    // Countdown timer
    let remaining = retryAfterSeconds;
    retryIntervalRef.current = setInterval(() => {
      remaining--;
      setRetryCountdown(remaining);
      if (remaining <= 0) {
        clearInterval(retryIntervalRef.current);
        retryIntervalRef.current = null;
      }
    }, 1000);

    // Schedule retry
    retryTimeoutRef.current = setTimeout(async () => {
      if (retryIntervalRef.current) {
        clearInterval(retryIntervalRef.current);
        retryIntervalRef.current = null;
      }
      setRetryCountdown(null);
      setError(null);
      setStep(STEPS.CREATING);
      
      try {
        // Use same idempotency key for retry (idempotency purpose)
        await processPaymentRequest(paymentRequest, paymentRequest._idempotencyKey);
      } catch (retryErr) {
        // If retry also gets 409 with Retry-After, schedule another retry
        if (retryErr.status === 409 && retryErr.data?.headers?.['retry-after']) {
          const newRetryAfter = parseInt(retryErr.data.headers['retry-after']) || 2;
          handleTemporaryConflict(newRetryAfter, paymentRequest);
        } else {
          setError(getErrorMessage(retryErr));
          setStep(STEPS.ERROR);
        }
      }
    }, retryAfterSeconds * 1000);
  };

  /**
   * Extracts error message from error object
   */
  const getErrorMessage = (err) => {
    return err.data?.message || err.data?.error || err.message || 'An error occurred';
  };

  /**
   * Handles 409 CONFLICT without Retry-After (permanent conflict)
   */
  const handlePermanentConflict = () => {
    setError('This idempotency key was already used with a different request. Please use a new idempotency key.');
    setStep(STEPS.ERROR);
  };

  /**
   * Processes payment creation request
   * Uses HTTP status codes as primary decision point
   */
  const processPaymentRequest = async (paymentRequest, idempotencyKey) => {
    const result = await createPayment(paymentRequest, idempotencyKey);
    const { payment, status } = result;
    setPaymentData(payment);

    // HTTP 202 ACCEPTED → Always poll (payment is processing asynchronously)
    if (status === 202) {
      await handlePaymentAccepted(payment);
      return;
    }

    // HTTP 201 CREATED / 200 OK → Use response body content to decide
    // (Same HTTP status can mean: success with clientSecret, declined, or pending replay)
    if (status === 201 || status === 200) {
      await handlePaymentCreated(payment);
      return;
    }

    // Unexpected status
    throw new Error(`Unexpected HTTP status: ${status}`);
  };

  // Payment creation entry point
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setStep(STEPS.CREATING);

    // Generate idempotency key (browser responsibility)
    const idempotencyKey = crypto.randomUUID();

    const paymentRequest = {
      orderId: orderId.trim(),
      buyerId: buyerId.trim(),
      totalAmount: { quantity: parseInt(totalAmount), currency },
      paymentOrders: paymentOrders.map(po => ({
        sellerId: po.sellerId.trim(),
        amount: { quantity: parseInt(po.amount), currency }
      }))
    };

    // Store idempotency key in request object for retries
    paymentRequest._idempotencyKey = idempotencyKey;

    try {
      await processPaymentRequest(paymentRequest, idempotencyKey);
    } catch (err) {
      // Handle 409 CONFLICT errors
      if (err.status === 409) {
        const retryAfter = err.data?.headers?.['retry-after'];
        
        if (retryAfter) {
          // Temporary conflict → retry createPayment after delay (uses same idempotency key)
          const retrySeconds = parseInt(retryAfter) || 2;
          handleTemporaryConflict(retrySeconds, paymentRequest);
        } else {
          // Permanent conflict → show error
          handlePermanentConflict();
        }
      } else {
        setError(getErrorMessage(err) || 'Failed to create payment');
        setStep(STEPS.ERROR);
      }
    }
  };

  // Payment authorization
  const handleAuthorize = async () => {
    if (!paymentIntentId) {
      setError('Payment ID missing');
      return;
    }

    setStep(STEPS.AUTHORIZING);
    setError(null);

    try {
      const result = await authorizePayment(paymentIntentId);
      const { status } = result.payment;

      if (status === 'AUTHORIZED' || status === 'SUCCEEDED' || status === 'REQUIRES_ACTION') {
        setStep(STEPS.SUCCESS);
      } else if (status === 'DECLINED' || status === 'FAILED') {
        setError(result.payment.error?.message || 'Payment declined');
        setStep(STEPS.ERROR);
      } else {
        setStep(STEPS.SUCCESS);
      }
    } catch (err) {
      setError(getErrorMessage(err) || 'Failed to authorize payment');
      setStep(STEPS.ERROR);
    }
  };

  const handleReset = () => {
    // Clear any pending retries
    if (retryTimeoutRef.current) {
      clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = null;
    }
    if (retryIntervalRef.current) {
      clearInterval(retryIntervalRef.current);
      retryIntervalRef.current = null;
    }
    // Reset all state
    setStep(STEPS.FORM);
    setPaymentData(null);
    setClientSecret(null);
    setPaymentIntentId(null);
    setError(null);
    setRetryCountdown(null);
  };

  return (
    <div className="app">
      <div className="header">
        <h1>💳 Payment Checkout</h1>
      </div>

      {step === STEPS.FORM && (
        <div className="container">
          <div className="card">
            <h2>Order Details</h2>
            {error && <div className="error-message">{error}</div>}
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Order ID *</label>
                <input type="text" value={orderId} onChange={(e) => setOrderId(e.target.value)} required />
              </div>

              <div className="form-group">
                <label>Buyer ID *</label>
                <input type="text" value={buyerId} onChange={(e) => setBuyerId(e.target.value)} required />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label>Total Amount (cents) *</label>
                  <input 
                    type="number" 
                    value={totalAmount} 
                    onChange={(e) => setTotalAmount(e.target.value)} 
                    min="1" 
                    required 
                  />
                  <small style={{ color: '#666', fontSize: '12px', display: 'block', marginTop: '4px' }}>
                    Calculated: {totalCalculated}
                  </small>
                </div>
                <div className="form-group">
                  <label>Currency *</label>
                  <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                    <option value="EUR">EUR</option>
                    <option value="USD">USD</option>
                    <option value="GBP">GBP</option>
                  </select>
                </div>
              </div>

              <h3 style={{ marginTop: '24px', marginBottom: '12px', fontSize: '16px' }}>Payment Orders</h3>
              
              {paymentOrders.map((po, index) => (
                <div key={po.id} className="seller-line">
                  <div className="seller-line-header">
                    <h3>Order #{index + 1}</h3>
                    {paymentOrders.length > 1 && (
                      <button 
                        type="button" 
                        onClick={() => removePaymentOrder(po.id)}
                        className="remove-btn"
                      >
                        Remove
                      </button>
                    )}
                  </div>
                  <div className="seller-line-fields">
                    <div className="form-group">
                      <label>Seller ID *</label>
                      <input
                        type="text"
                        value={po.sellerId}
                        onChange={(e) => updatePaymentOrder(po.id, 'sellerId', e.target.value)}
                        placeholder="SELLER-111"
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label>Amount (cents) *</label>
                      <input
                        type="number"
                        value={po.amount}
                        onChange={(e) => updatePaymentOrder(po.id, 'amount', e.target.value)}
                        min="1"
                        required
                      />
                    </div>
                  </div>
                </div>
              ))}

              <button 
                type="button" 
                onClick={addPaymentOrder} 
                className="add-seller-btn"
              >
                + Add Payment Order
              </button>

              <button type="submit" className="submit-btn">Proceed to Checkout</button>
            </form>
          </div>
        </div>
      )}

      {step === STEPS.CREATING && (
        <LoadingScreen 
          message={
            retryCountdown !== null 
              ? `Payment is still being processed. Retrying in ${retryCountdown} seconds...`
              : "Creating payment intent..."
          } 
        />
      )}

      {step === STEPS.PAYMENT && clientSecret && (
        <div className="container">
          <div className="card">
            <h2>💳 Payment</h2>
            {paymentData && (
              <div className="order-summary">
                <p><strong>Order:</strong> {paymentData.orderId}</p>
                <p><strong>Amount:</strong> {(paymentData.totalAmount.quantity / 100).toFixed(2)} {paymentData.totalAmount.currency}</p>
              </div>
            )}
            {error && <div className="error-message">{error}</div>}
            <Elements stripe={stripePromise} options={{ clientSecret }}>
              <PaymentForm onPaymentSubmit={handleAuthorize} onError={(err) => setError(err.message)} />
            </Elements>
          </div>
        </div>
      )}

      {step === STEPS.AUTHORIZING && (
        <LoadingScreen message="Confirming payment..." />
      )}

      {step === STEPS.SUCCESS && (
        <div className="container">
          <div className="card">
            <h2>✅ Success!</h2>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>🎉</div>
              <p>Payment processed successfully</p>
              {paymentData && (
                <div className="order-summary" style={{ textAlign: 'left', margin: '20px 0' }}>
                  <p><strong>Order:</strong> {paymentData.orderId}</p>
                  <p><strong>Amount:</strong> {(paymentData.totalAmount.quantity / 100).toFixed(2)} {paymentData.totalAmount.currency}</p>
                </div>
              )}
              <button onClick={handleReset} className="submit-btn" style={{ marginTop: '20px' }}>
                New Payment
              </button>
            </div>
          </div>
        </div>
      )}

      {step === STEPS.ERROR && (
        <div className="container">
          <div className="card">
            <h2>❌ Error</h2>
            {error && <div className="error-message">{error}</div>}
            <div style={{ textAlign: 'center', padding: '20px' }}>
              <button onClick={handleReset} className="submit-btn">Try Again</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Reusable loading component
function LoadingScreen({ message }) {
  return (
    <div className="container">
      <div className="card">
        <h2>⏳ {message}</h2>
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <div className="loading-spinner" style={{ width: '40px', height: '40px', margin: '0 auto 20px' }}></div>
          <p>{message}</p>
        </div>
      </div>
    </div>
  );
}

export default App;
