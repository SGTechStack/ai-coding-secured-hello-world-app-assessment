# MCNS Core — Implementation Questions

Questions to resolve with the team or product owner before starting implementation.

---

### MCNS onboarding

1. Has the service been onboarded onto MCNS with an approved message template for each notification type? MCNS will reject requests from services without an active approved template.
2. Has the MCNS sender identity been registered with the relevant registry? Unregistered senders appear as "Likely SCAM" or land in spam on recipient devices.
---

### Channel

3. What notification channel is required for each use case — `email`, `sms`, or both? *(See Core Standard §3.1 design choice: Channel selection)*
   > The channel determines recipient format validation and the endpoint path used for the request. 

4. If both `email` and `sms` are used: is the channel selected per user (based on a stored preference), per notification type (fixed at design time), or per request (caller-supplied at runtime)?

---

### Retry

5. Is automatic retry on transient MCNS failures required? *(See Core Standard §4 design choice: Retry)*
   > Retry is optional. Without it, a transient MCNS failure propagates immediately to the caller. If the application has its own downstream retry or a batch retry pipeline (see MCNS Batch Standard), synchronous retry may be redundant.

6. If retry is enabled: what values are appropriate for `maxRetries` and `retryWaitDuration`? The defaults are 3 retries and 5-second wait. Consider the caller's response latency tolerance — each retry adds `retryWaitDuration` to the total wait.

---

### Rate limiting

7. Is outbound rate limiting required to protect the MCNS service from burst traffic? *(See Core Standard §4 design choice: Rate limiting)*
    > Rate limiting is optional. Use it if the application can generate bursts that risk exceeding MCNS capacity. Requests that exceed the window are rejected before any network call — the caller receives an error immediately.

8. If rate limiting is enabled: what values are appropriate for `rateLimitPerPeriod` and `limitRefreshPeriod`? These must be set to match the MCNS service capacity limits agreed during onboarding.

---

### Caller context

9. Is the send service called from a standard blocking request-response handler, or from a reactive context? *(See Core Standard §4 design choice: Blocking send)*
    > The synchronous send is blocking. Reactive callers should apply an external timeout wrapper around the send service call.


10. What should the caller do when a send fails after all retries are exhausted — surface an error to the user, queue the notification for batch retry, add notification to a dead letter queue, or silently discard?
    > If batch retry is configured, failed synchronous sends can be re-queued as domain records with `isProcessed = false` for the batch pipeline to pick up.

---

### Operational

11. What environment URLs are configured for each deployment environment (Dev, SIT, QA, Production)? Confirm each URL against the values in §3.1 before deployment.
12. What is the expected notification volume — sends per minute and peak burst? This determines whether rate limiting is necessary and how to size the retry budget.
