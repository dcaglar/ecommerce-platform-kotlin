import { useState } from 'react';
import { PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';

export function PaymentForm({ onPaymentSubmit, onError }) {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!stripe || !elements) return;

    setIsProcessing(true);
    setError(null);

    try {
      const { error: submitError } = await elements.submit();
      if (submitError) {
        setError(submitError.message);
        onError?.(submitError);
        return;
      }
      onPaymentSubmit();
    } catch (err) {
      setError(err.message || 'An error occurred');
      onError?.(err);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="payment-element-container">
        <PaymentElement options={{ layout: 'tabs' }} />
      </div>
      {error && <div className="payment-error">{error}</div>}
      <button type="submit" className="pay-button" disabled={!stripe || !elements || isProcessing}>
        {isProcessing ? 'Processing...' : 'Pay Now'}
      </button>
    </form>
  );
}
