Currently we had integrated create payment intent api with our system.

now we wouldl like to process webhook events(payment.intent.created) which is gonna be sentt by Stripe


example payload webhook

{
"object": {
"id": "pi_3SjzIyEAJKUKtoJw1gSSWET2",
"object": "payment_intent",
"amount": 2000,
"amount_capturable": 0,
"amount_details": {
"tip": {}
},
"amount_received": 0,
"application": null,
"application_fee_amount": null,
"automatic_payment_methods": null,
"canceled_at": null,
"cancellation_reason": null,
"capture_method": "automatic_async",
"client_secret": "pi_3SjzIyEAJKUKtoJw1gSSWET2_secret_U53kdlNnk9glKvkXkUbvFtyC4",
"confirmation_method": "automatic",
"created": 1767087208,
"currency": "usd",
"customer": null,
"customer_account": null,
"description": "(created by Stripe CLI)",
"excluded_payment_method_types": null,
"last_payment_error": null,
"latest_charge": null,
"livemode": false,
"metadata": {},
"next_action": null,
"on_behalf_of": null,
"payment_method": null,
"payment_method_configuration_details": null,
"payment_method_options": {
"card": {
"installments": null,
"mandate_options": null,
"network": null,
"request_three_d_secure": "automatic"
}
},
"payment_method_types": [
"card"
],
"processing": null,
"receipt_email": null,
"review": null,
"setup_future_usage": null,
"shipping": {
"address": {
"city": "San Francisco",
"country": "US",
"line1": "510 Townsend St",
"line2": null,
"postal_code": "94103",
"state": "CA"
},
"carrier": null,
"name": "Jenny Rosen",
"phone": null,
"tracking_number": null
},
"source": null,
"statement_descriptor": null,
"statement_descriptor_suffix": null,
"status": "requires_payment_method",
"transfer_data": null,
"transfer_group": null
},
"previous_attributes": null
}



Create a new endpoint

settings
A webhook endpoint is a destination on your server that receives requests from Stripe, notifying you about events that happen on your account such as a customer disputing a charge or a successful recurring payment. Add a new endpoint to your server and make sure it’s publicly accessible so we can send unauthenticated POST requests.


an example java code for processing webhook event

post("/webhook", (request, response) -> {
String payload = request.body();
Event event = null;

            try {
                event = ApiResource.GSON.fromJson(payload, Event.class);
            } catch (JsonSyntaxException e) {
                // Invalid payload
                System.out.println("⚠️  Webhook error while parsing basic request.");
                response.status(400);
                return "";
            }
            String sigHeader = req`uest.headers("Stripe-Signature");
            if(endpointSecret != null && sigHeader != null) {
                // Only verify the event if you have an endpoint secret defined.
                // Otherwise use the basic event deserialized with GSON.
                try {
                    event = Webhook.constructEvent(
                        payload, sigHeader, endpointSecret
                    );
                } catch (SignatureVerificationException e) {
                    // Invalid signature
                    System.out.println("⚠️  Webhook error while validating signature.");
                    response.status(400);
                    return "";
                }
            }
            // Deserialize the nested object inside the event
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = null;
            if (dataObjectDeserializer.getObject().isPresent()) {
                stripeObject = dataObjectDeserializer.getObject().get();
            } else {
                // Deserialization failed, probably due to an API version mismatch.
                // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
                // instructions on how to handle this case, or return an error here.
            }
            // Handle the event
            switch (event.getType()) {
                case "payment_intent.creaeted":
                    PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                    System.out.println("Payment for " + paymentIntent.getAmount() + " succeeded.");
                    // Then define and call a method to handle the successful payment intent.
                    // handlePaymentIntentSucceeded(paymentIntent);
                    break;
                case "payment_method.attached":
                    PaymentMethod paymentMethod = (PaymentMethod) stripeObject;
                    // Then define and call a method to handle the successful attachment of a PaymentMethod.
                    // handlePaymentMethodAttached(paymentMethod);
                    break;
                default:
                    System.out.println("Unhandled event type: " + event.getType());
                break;
            }
            response.status(200);
            return "";
        });



pamyent controller can not call usecase directly, every thing goes through PaymentService, and it shoulx call usecases, and map stripe specific dto's to generic webhook, or paymentintenupdate events, and pass them to new use case 

