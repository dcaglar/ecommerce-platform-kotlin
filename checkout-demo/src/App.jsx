import { useState, useEffect } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { Elements } from '@stripe/react-stripe-js';
import { PaymentForm } from './components/PaymentElement';
import { createPayment, pollPaymentStatus, authorizePayment } from './services/paymentService';
import './index.css';

// Initialize Stripe - you'll need to set VITE_STRIPE_PUBLISHABLE_KEY in .env
const stripePromise = loadStripe(
  import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY || 'pk_test_placeholder'
);

function App() {
  // Payment form state
  const [orderId, setOrderId] = useState('')
  const [buyerId, setBuyerId] = useState('')
  const [totalAmountQuantity, setTotalAmountQuantity] = useState('')
  const [totalAmountCurrency, setTotalAmountCurrency] = useState('EUR')
  const [paymentOrders, setPaymentOrders] = useState([
    { id: 1, sellerId: 'SELLER-111', quantity: '', currency: 'EUR' }
  ])

  // Payment flow state
  const [step, setStep] = useState('form'); // 'form' | 'creating' | 'payment' | 'authorizing' | 'success' | 'error'
  const [paymentData, setPaymentData] = useState(null); // Stores payment response
  const [clientSecret, setClientSecret] = useState(null);
  const [paymentIntentId, setPaymentIntentId] = useState(null);
  const [error, setError] = useState(null);
  const [polling, setPolling] = useState(false);

  const addPaymentOrder = () => {
    const newId = Math.max(...paymentOrders.map(po => po.id), 0) + 1
    setPaymentOrders([
      ...paymentOrders,
      { id: newId, sellerId: '', quantity: '', currency: 'EUR' }
    ])
  }

  const removePaymentOrder = (id) => {
    if (paymentOrders.length <= 1) {
      alert('At least one payment order is required')
      return
    }
    setPaymentOrders(paymentOrders.filter(po => po.id !== id))
  }

  const updatePaymentOrder = (id, field, value) => {
    setPaymentOrders(paymentOrders.map(po => 
      po.id === id ? { ...po, [field]: value } : po
    ))
  }

  const calculateTotal = () => {
    return paymentOrders.reduce((sum, po) => {
      const qty = parseInt(po.quantity) || 0
      return sum + qty
    }, 0)
  }

  const validateForm = () => {
    if (!orderId.trim()) {
      return 'Order ID is required'
    }
    if (!buyerId.trim()) {
      return 'Buyer ID is required'
    }
    if (!totalAmountQuantity || parseInt(totalAmountQuantity) <= 0) {
      return 'Total amount quantity must be greater than 0'
    }
    if (!totalAmountCurrency) {
      return 'Total amount currency is required'
    }
    if (paymentOrders.length === 0) {
      return 'At least one payment order is required'
    }
    for (let i = 0; i < paymentOrders.length; i++) {
      const po = paymentOrders[i]
      if (!po.sellerId.trim()) {
        return `Payment order ${i + 1}: Seller ID is required`
      }
      if (!po.quantity || parseInt(po.quantity) <= 0) {
        return `Payment order ${i + 1}: Amount quantity must be greater than 0`
      }
      if (!po.currency) {
        return `Payment order ${i + 1}: Currency is required`
      }
    }
    
    // Validate total matches sum
    const calculatedTotal = calculateTotal()
    const providedTotal = parseInt(totalAmountQuantity) || 0
    if (calculatedTotal !== providedTotal) {
      return `Total amount (${providedTotal}) does not match sum of payment orders (${calculatedTotal})`
    }
    
    // Validate all currencies match
    const currencies = [totalAmountCurrency, ...paymentOrders.map(po => po.currency)]
    const uniqueCurrencies = [...new Set(currencies)]
    if (uniqueCurrencies.length > 1) {
      return 'All amounts must use the same currency'
    }
    
    return null
  }

  // Handle payment creation
  const handleProceedToCheckout = async (e) => {
    e.preventDefault()
    
    // Validate form
    const validationError = validateForm()
    if (validationError) {
      setError(validationError)
      return
    }

    setError(null)
    setStep('creating')

    try {
      // Build payment request
      console.log('📝 [App] Building payment request...');
      const paymentRequest = {
        orderId: orderId.trim(),
        buyerId: buyerId.trim(),
        totalAmount: {
          quantity: parseInt(totalAmountQuantity),
          currency: totalAmountCurrency
        },
        paymentOrders: paymentOrders.map(po => ({
          sellerId: po.sellerId.trim(),
          amount: {
            quantity: parseInt(po.quantity),
            currency: po.currency
          }
        }))
      }
      console.log('   Payment data:', JSON.stringify(paymentRequest, null, 2));

      // Step 1: Create payment
      console.log('📤 [App] Creating payment...');
      const result = await createPayment(paymentRequest)
      console.log('✅ [App] Payment creation response:', result);

      const payment = result.payment;
      const status = result.status;

      // Store payment data
      setPaymentData(payment);

      // Handle different response statuses
      if (status === 201 || status === 200) {
        // Success: Payment created with client secret
        if (payment.clientSecret && payment.paymentIntentId) {
          console.log('✅ [App] Payment created successfully with client secret');
          setClientSecret(payment.clientSecret);
          setPaymentIntentId(payment.paymentIntentId);
          setStep('payment');
        } else {
          // Missing client secret but status is success - should not happen
          console.warn('⚠️ [App] Payment created but missing client secret');
          setError('Payment created but client secret is missing. Please try again.');
          setStep('error');
        }
      } else if (status === 202) {
        // Pending: Payment created but Stripe call is in progress
        console.log('⏳ [App] Payment creation is pending, starting polling...');
        if (payment.paymentIntentId) {
          setPaymentIntentId(payment.paymentIntentId);
          setPolling(true);
          // Start polling for client secret
          pollForClientSecret(payment.paymentIntentId);
        } else {
          setError('Payment creation is pending but payment ID is missing. Please try again.');
          setStep('error');
        }
      } else {
        setError('Unexpected response status: ' + status);
        setStep('error');
      }
    } catch (error) {
      console.error('❌ [App] Payment creation failed:', error);
      setError(error.message || 'Failed to create payment');
      setStep('error');
    }
  }

  // Poll for client secret when payment is pending
  const pollForClientSecret = async (id) => {
    try {
      console.log('🔄 [App] Starting to poll for client secret...');
      const result = await pollPaymentStatus(id);
      
      if (result.payment && result.payment.clientSecret) {
        console.log('✅ [App] Client secret received from polling');
        setClientSecret(result.payment.clientSecret);
        setPaymentIntentId(result.payment.paymentIntentId || id);
        setPolling(false);
        setStep('payment');
      } else {
        throw new Error('Client secret not available after polling');
      }
    } catch (error) {
      console.error('❌ [App] Polling failed:', error);
      setPolling(false);
      setError(error.message || 'Failed to retrieve payment details. Please try again.');
      setStep('error');
    }
  }

  // Handle payment authorization (after Payment Element submit)
  const handleAuthorizePayment = async () => {
    if (!paymentIntentId) {
      setError('Payment ID is missing');
      return;
    }

    setStep('authorizing');
    setError(null);

    try {
      console.log('🔐 [App] Authorizing payment...');
      console.log('   Payment Intent ID:', paymentIntentId);
      console.log('   Note: No payment details sent - backend uses stored PaymentIntent ID');
      const result = await authorizePayment(paymentIntentId);
      console.log('✅ [App] Payment authorization response:', result);

      const payment = result.payment;

      // Check payment status
      if (payment.status === 'AUTHORIZED' || payment.status === 'SUCCEEDED') {
        console.log('✅ [App] Payment authorized successfully');
        setStep('success');
      } else if (payment.status === 'REQUIRES_ACTION' || payment.status === 'REQUIRES_CONFIRMATION') {
        // 3DS authentication required
        console.log('🔐 [App] 3DS authentication required');
        // For now, we'll show success - in production, you'd handle 3DS flow
        setStep('success');
      } else if (payment.status === 'DECLINED' || payment.status === 'FAILED') {
        setError(payment.error?.message || 'Payment was declined. Please try a different payment method.');
        setStep('error');
      } else {
        // Unknown status
        console.warn('⚠️ [App] Unknown payment status:', payment.status);
        setStep('success'); // Assume success for now
      }
    } catch (error) {
      console.error('❌ [App] Payment authorization failed:', error);
      setError(error.message || 'Failed to authorize payment');
      setStep('error');
    }
  }

  // Reset to start over
  const handleReset = () => {
    setStep('form');
    setPaymentData(null);
    setClientSecret(null);
    setPaymentIntentId(null);
    setError(null);
    setPolling(false);
  }

  return (
    <div className="app">
      <div className="header">
        <h1>💳 Payment Checkout Demo</h1>
        <p>Complete end-to-end payment flow with Stripe Payment Element</p>
      </div>

      {step === 'form' && (
        <div className="container">
          <div className="card">
            <h2>📝 Order Details</h2>
            
            {error && (
              <div className="error-message">
                <strong>Error:</strong> {error}
              </div>
            )}

            <form onSubmit={handleProceedToCheckout}>
              <div className="form-group">
                <label htmlFor="orderId">Order ID *</label>
                <input
                  id="orderId"
                  type="text"
                  value={orderId}
                  onChange={(e) => setOrderId(e.target.value)}
                  placeholder="ORDER-12345"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="buyerId">Buyer ID *</label>
                <input
                  id="buyerId"
                  type="text"
                  value={buyerId}
                  onChange={(e) => setBuyerId(e.target.value)}
                  placeholder="BUYER-123"
                  required
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label htmlFor="totalAmountQuantity">Total Amount (quantity in cents) *</label>
                  <input
                    id="totalAmountQuantity"
                    type="number"
                    value={totalAmountQuantity}
                    onChange={(e) => setTotalAmountQuantity(e.target.value)}
                    placeholder="5000"
                    min="1"
                    required
                  />
                  <small style={{ color: '#666', fontSize: '12px', marginTop: '4px', display: 'block' }}>
                    Calculated total: {calculateTotal()}
                  </small>
                </div>

                <div className="form-group">
                  <label htmlFor="totalAmountCurrency">Currency *</label>
                  <select
                    id="totalAmountCurrency"
                    value={totalAmountCurrency}
                    onChange={(e) => setTotalAmountCurrency(e.target.value)}
                    required
                  >
                    <option value="EUR">EUR</option>
                    <option value="USD">USD</option>
                    <option value="GBP">GBP</option>
                  </select>
                </div>
              </div>

              <h3 style={{ marginTop: '24px', marginBottom: '12px', fontSize: '16px', color: '#555' }}>
                Payment Orders
              </h3>

              {paymentOrders.map((po, index) => (
                <div key={po.id} className="seller-line">
                  <div className="seller-line-header">
                    <h3>Payment Order #{index + 1}</h3>
                    <button
                      type="button"
                      onClick={() => removePaymentOrder(po.id)}
                      className="remove-btn"
                    >
                      Remove
                    </button>
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
                      <label>Amount (quantity) *</label>
                      <input
                        type="number"
                        value={po.quantity}
                        onChange={(e) => updatePaymentOrder(po.id, 'quantity', e.target.value)}
                        placeholder="2500"
                        min="1"
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label>Currency *</label>
                      <select
                        value={po.currency}
                        onChange={(e) => updatePaymentOrder(po.id, 'currency', e.target.value)}
                        required
                      >
                        <option value="EUR">EUR</option>
                        <option value="USD">USD</option>
                        <option value="GBP">GBP</option>
                      </select>
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

              <button
                type="submit"
                className="submit-btn"
              >
                Proceed to Checkout
              </button>
            </form>
          </div>
        </div>
      )}

      {step === 'creating' && (
        <div className="container">
          <div className="card">
            <h2>⏳ Creating Payment</h2>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div className="loading-spinner" style={{ width: '40px', height: '40px', margin: '0 auto 20px' }}></div>
              <p>Creating your payment intent...</p>
            </div>
          </div>
        </div>
      )}

      {step === 'payment' && clientSecret && (
        <div className="container">
          <div className="card">
            <h2>💳 Payment Details</h2>
            
            {paymentData && (
              <div className="order-summary">
                <h3>Order Summary</h3>
                <p><strong>Order ID:</strong> {paymentData.orderId}</p>
                <p><strong>Buyer ID:</strong> {paymentData.buyerId}</p>
                <p><strong>Amount:</strong> {(paymentData.totalAmount.quantity / 100).toFixed(2)} {paymentData.totalAmount.currency}</p>
                <p><strong>Status:</strong> {paymentData.status}</p>
              </div>
            )}

            {error && (
              <div className="error-message">
                <strong>Error:</strong> {error}
              </div>
            )}

            <Elements stripe={stripePromise} options={{ clientSecret }}>
              <PaymentForm
                clientSecret={clientSecret}
                onPaymentSubmit={handleAuthorizePayment}
                onError={(err) => {
                  setError(err.message || 'Payment submission failed');
                }}
              />
            </Elements>
          </div>
        </div>
      )}

      {polling && (
        <div className="container">
          <div className="card">
            <h2>⏳ Processing Payment</h2>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div className="loading-spinner" style={{ width: '40px', height: '40px', margin: '0 auto 20px' }}></div>
              <p>Your payment is being processed...</p>
              <p style={{ fontSize: '14px', color: '#666', marginTop: '10px' }}>
                Waiting for payment details to become available
              </p>
            </div>
          </div>
        </div>
      )}

      {step === 'authorizing' && (
        <div className="container">
          <div className="card">
            <h2>🔐 Authorizing Payment</h2>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div className="loading-spinner" style={{ width: '40px', height: '40px', margin: '0 auto 20px' }}></div>
              <p>Confirming your payment...</p>
            </div>
          </div>
        </div>
      )}

      {step === 'success' && (
        <div className="container">
          <div className="card">
            <h2>✅ Payment Successful!</h2>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>🎉</div>
              <p style={{ fontSize: '18px', marginBottom: '20px' }}>Your payment has been processed successfully.</p>
              {paymentData && (
                <div className="order-summary" style={{ textAlign: 'left', margin: '20px 0' }}>
                  <h3>Order Details</h3>
                  <p><strong>Order ID:</strong> {paymentData.orderId}</p>
                  <p><strong>Payment ID:</strong> {paymentIntentId}</p>
                  <p><strong>Amount:</strong> {(paymentData.totalAmount.quantity / 100).toFixed(2)} {paymentData.totalAmount.currency}</p>
                  <p><strong>Status:</strong> {paymentData.status}</p>
                </div>
              )}
              <button onClick={handleReset} className="submit-btn" style={{ marginTop: '20px' }}>
                Start New Payment
              </button>
            </div>
          </div>
        </div>
      )}

      {step === 'error' && (
        <div className="container">
          <div className="card">
            <h2>❌ Payment Error</h2>
            {error && (
              <div className="error-message">
                <strong>Error:</strong> {error}
              </div>
            )}
            <div style={{ textAlign: 'center', padding: '20px' }}>
              <button onClick={handleReset} className="submit-btn">
                Try Again
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
