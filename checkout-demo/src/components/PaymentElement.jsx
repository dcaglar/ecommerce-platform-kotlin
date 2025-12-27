import { useState } from 'react';
import { PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';

export function PaymentForm({ clientSecret, onPaymentSubmit, onError }) {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!stripe || !elements || !clientSecret) return;

    setIsProcessing(true);
    setError(null);

    try {
      // elements.submit() does the following:
      // 1. Validates the payment information entered by the customer
      //    - Checks card number format, expiration date, CVC, etc.
      //    - Validates billing address fields if present
      //    - Returns validation errors if information is incomplete or invalid
      // 2. Securely sends payment details to Stripe
      //    - Tokenizes the card or other payment method
      //    - Associates it with your PaymentIntent (via clientSecret)
      //    - Creates or updates the PaymentMethod on Stripe's servers
      // 3. CRUCIAL: Does NOT confirm the payment
      //    - Unlike confirmPayment(), it doesn't complete the payment
      //    - Doesn't charge the customer's card
      //    - Merely prepares everything for confirmation
      const { error: submitError } = await elements.submit();

      if (submitError) {
        setError(submitError.message);
        onError?.(submitError);
        setIsProcessing(false);
        return;
      }

      // Payment method is now associated with PaymentIntent (but NOT confirmed)
      // Card data has been sent directly from browser to Stripe (never touches our servers)
      // Now call the backend authorize endpoint to confirm/authorize the payment
      onPaymentSubmit();
      
    } catch (err) {
      setError(err.message || 'An error occurred while processing payment');
      onError?.(err);
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
