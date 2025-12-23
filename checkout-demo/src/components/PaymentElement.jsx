import { useState } from 'react';
import { PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';

/**
 * Payment Element component that handles Stripe payment form
 */
export function PaymentForm({ clientSecret, onPaymentSubmit, onError }) {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!stripe || !elements) {
      // Stripe.js hasn't loaded yet
      return;
    }

    setIsProcessing(true);
    setError(null);

    try {
      // Submit payment details to Stripe
      // This validates the card and associates payment method with PaymentIntent
      // No payment is confirmed yet - that happens in the authorize step
      const { error: submitError } = await elements.submit();
      
      if (submitError) {
        console.error('❌ [PaymentForm] Payment Element submit error:', submitError);
        setError(submitError.message || 'Failed to submit payment details');
        onError?.(submitError);
        setIsProcessing(false);
        return;
      }

      console.log('✅ [PaymentForm] Payment details submitted successfully');
      // Payment details are now associated with the PaymentIntent via Stripe
      // No payment details are sent to backend - backend will use stored PaymentIntent ID
      // Call the authorize endpoint to confirm the payment
      onPaymentSubmit();
    } catch (err) {
      console.error('❌ [PaymentForm] Unexpected error:', err);
      setError(err.message || 'An unexpected error occurred');
      onError?.(err);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <form id="payment-form" onSubmit={handleSubmit}>
      <div className="payment-element-container">
        <PaymentElement 
          id="payment-element"
          options={{
            layout: 'tabs'
          }}
        />
      </div>
      
      {error && (
        <div className="payment-error" role="alert">
          {error}
        </div>
      )}

      <button
        id="submit"
        type="submit"
        className="pay-button"
        disabled={!stripe || !elements || isProcessing}
      >
        {isProcessing ? (
          <>
            <span className="loading-spinner" style={{ marginRight: '8px' }}></span>
            Processing...
          </>
        ) : (
          'Pay Now'
        )}
      </button>
    </form>
  );
}

