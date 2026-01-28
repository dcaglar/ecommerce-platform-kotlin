The Core Mechanism: The Idempotency Check
You should implement a check at the very beginning of your controller (or via middleware). This usually involves looking up the Idempotency-Key header in a high-speed store (for now normal db) that maps the key to the request status, the payload hash, and the stored response.
Here are the four specific scenarios you must handle:
Scenario 1: The "Happy Path" (First Request)
• Condition: The Idempotency-Key does not exist in the store.
• Action:
1. Create a record in db with status PENDING (Atomic operation/Lock).
2. Process the payment (call downstream services, and stripe).
3. Update the idempoitency record with status COMPLETED(when should this happen given createpaymentintent means persiting intensts internally plus cakk stripe api) and save the response body.
• Return: HTTP 201 Created (Assuming a new payment resource was created).
Scenario 2: The "In-Flight" Race Condition (Concurrent Requests)
• Condition: The Idempotency-Key exists, but the status is PENDING.
• Context: This happens when a client double-clicks rapidly, or a network timeout causes a retry while the server is still processing the first request.
• The Bug/Risk: If you don't check this status, you might process both threads in parallel, leading to a race condition.
• Return: HTTP 409 Conflict.
◦ Why? Source explicitly notes that for Mollie interviews, 409 Conflict is the expected answer here. It signals to the client: "The request conflicts with the current state of the server (it is already working on this key)."
Scenario 3: The "True Retry" (Processing Complete)
• Condition: The Idempotency-Key exists, and status is COMPLETED.
• Verification: You must hash the incoming request body (payload) and compare it to the hash stored with the key.
• Sub-Scenario 3A: Hash Matches (Valid Retry)
◦ Action: Do not process the payment again. Retrieve the stored response from Redis.
◦ Return: HTTP 200 OK (or 201 Created).
◦ Note: You return the original success response. This makes the operation transparent to the client,.
Scenario 4: The "Key Reuse" (Mismatch/Tampering)
• Condition: The Idempotency-Key exists, status is COMPLETED (or IN_PROGRESS), but the Payload Hash does not match.
• Context: The client is trying to use an old key for a new or modified payment (e.g., changing the amount from $10 to $100 but keeping the key).
• The Risk: If you ignore the hash check, you might return a cached success for a $10 transaction while the user thinks they paid $100, or vice versa.
• Return: HTTP 422 Unprocessable Entity (or 400 Bad Request).
◦ Why? The request is semantically invalid because an Idempotency Key is a unique constraint bound to a specific payload state,.

--------------------------------------------------------------------------------
