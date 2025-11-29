import { useState } from 'react'
import { createPayment, generateCurlCommand } from './services/paymentService'
import './index.css'

function App() {
  // Payment form state
  const [orderId, setOrderId] = useState('')
  const [buyerId, setBuyerId] = useState('')
  const [totalAmountQuantity, setTotalAmountQuantity] = useState('')
  const [totalAmountCurrency, setTotalAmountCurrency] = useState('EUR')
  const [paymentOrders, setPaymentOrders] = useState([
    { id: 1, sellerId: 'SELLER-111', quantity: '', currency: 'EUR' }
  ])

  // Response state
  const [response, setResponse] = useState(null)
  const [responseLoading, setResponseLoading] = useState(false)
  const [responseError, setResponseError] = useState(null)
  const [curlCommand, setCurlCommand] = useState('')
  const [responseMetadata, setResponseMetadata] = useState(null)

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

  const handleSubmit = async (e) => {
    e.preventDefault()
    
    // Validate form
    const validationError = validateForm()
    if (validationError) {
      setResponseError(validationError)
      setResponse(null)
      return
    }

    setResponseLoading(true)
    setResponseError(null)
    setResponse(null)
    setCurlCommand('')
    setResponseMetadata(null)

    try {
      // Build payment request
      console.log('📝 [App] Building payment request...');
      const paymentData = {
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
      console.log('   Payment data:', JSON.stringify(paymentData, null, 2));

      // Send payment request via backend proxy (handles token + payment-service call)
      // This simulates production flow where frontend calls backend, backend handles auth
      console.log('📤 [App] Sending payment request to proxy...');
      const responseData = await createPayment(paymentData)
      console.log('✅ [App] Received response:', responseData);

      // Extract payment response and metadata
      const paymentResult = responseData.payment || responseData
      const metadata = responseData._meta || null
      
      setResponse(paymentResult)
      setResponseMetadata(metadata)
      
      // Generate curl command (using metadata if available)
      const curl = generateCurlCommand(paymentData, metadata)
      setCurlCommand(curl)
      
    } catch (error) {
      console.error('❌ [App] Payment request failed:', error);
      console.error('   Error details:', {
        status: error.status,
        statusText: error.statusText,
        message: error.message,
        data: error.data
      });
      setResponseError(error.message || 'Failed to create payment')
      if (error.data) {
        setResponse(error.data)
      }
    } finally {
      setResponseLoading(false)
      console.log('🏁 [App] Payment request completed');
    }
  }

  return (
    <div className="app">
      <div className="header">
        <h1>💳 Payment Checkout Demo</h1>
        <p>Developer tool for testing payment creation requests (production-like flow)</p>
      </div>

      <div className="info-message" style={{ marginBottom: '20px' }}>
        <strong>💡 Production-like flow:</strong> The backend proxy (simulating order-service/checkout-service) handles token acquisition and payment-service calls automatically. Just fill the form and click "Send Payment Request"!
      </div>

      <div className="container">
        {/* Payment Form */}
        <div className="card">
          <h2>📝 Payment Details</h2>
          
          <form onSubmit={handleSubmit}>
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
              disabled={responseLoading}
            >
              {responseLoading ? (
                <>
                  <span className="loading-spinner" style={{ marginRight: '8px' }}></span>
                  Sending Payment Request...
                </>
              ) : (
                'Send Payment Request'
              )}
            </button>
          </form>
        </div>
      </div>

      {/* Response Section */}
      {(response || responseError) && (
        <div className="card response-section">
          <h2>📤 Response</h2>
          
          {responseError && (
            <div className="error-message">
              <strong>Error:</strong> {responseError}
            </div>
          )}

          {response && (
            <div className={`response-box ${responseError ? 'response-error' : 'response-success'}`}>
              <pre>{JSON.stringify(response, null, 2)}</pre>
            </div>
          )}

          {curlCommand && (
            <div style={{ marginTop: '20px' }}>
              <h3 style={{ marginBottom: '12px', fontSize: '16px', color: '#555' }}>
                Equivalent curl command:
              </h3>
              <div className="curl-box">
                {curlCommand}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default App

