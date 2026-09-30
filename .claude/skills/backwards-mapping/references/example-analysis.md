# Example Analysis: E-Commerce Checkout System

This example demonstrates the "Right-To-Left Business Dependency Backwards Mapping" and "Vertical Slicing" methodology for an e-commerce checkout system.

## 1. The Right-To-Left Business Dependency Backwards Mapping

- **[Order Fulfillment Initiation]** -> Depends on -> **[Payment Capture Confirmation]**
  - **Reasoning**: We cannot fulfill an order until financial liability is successfully transferred.
- **[Payment Capture Confirmation]** -> Depends on -> **[Inventory Reservation]**
  - **Reasoning**: We cannot capture payment for goods we haven't secured; doing so risks "overselling" and customer friction.
- **[Inventory Reservation]** -> Depends on -> **[Shipping/Tax Calculation]**
  - **Reasoning**: Inventory must be reserved against a specific shipping destination to ensure logic (e.g., regional restrictions) and costs are finalized.
- **[Shipping/Tax Calculation]** -> Depends on -> **[Cart Finalization]**
  - **Reasoning**: You cannot calculate taxes or shipping without knowing the exact line items and the destination.

## 2. Cross-Cutting Concerns & Security Boundaries

### Role-Based Access Control (RBAC)

- **Customer**: `write` access to Checkout Session (initiate, add shipping info), `read` access to Order Status.
- **Payment Gateway (Service Principal)**: `write` access to Payment Confirmation state.
- **Fulfillment System (Service Principal)**: `read` access to finalized Orders.

### Session & State Constraints

- **Inventory Lock TTL**: Inventory reservations must expire after 15 minutes if payment is not confirmed.
- **Idempotency**: Payment capture must be idempotent per `CheckoutSessionID` to prevent double-charging.
- **State Transition**: A Checkout Session can only transition to `Paid` if the current state is `AwaitingPayment`.

### Compliance & Audit Trails

- **Audit**: Log every state transition of the `CheckoutSession` (e.g., `Created -> ShippingSet -> TaxCalculated -> PaymentInitiated`).
- **Telemetry**: Log latency for external Tax and Payment API calls.

## 3. The Execution Plan (Minimal Vertical Slice)

The "Walking Skeleton" for this architecture:

1. **Entry Point**: A `POST /checkout` endpoint that accepts a Cart ID.
2. **Security**: Verifies the User Session (RBAC).
3. **Core Logic**:
    - Finalizes the Cart (Cart Service dependency).
    - Reserves Inventory (Inventory Service dependency).
    - Transitions Checkout State to `In Progress`.
4. **Audit**: Writes a `CHECKOUT_INITIATED` event to the Audit Log.
5. **Outcome**: Returns a `CheckoutSessionID`.

This slice proves that we can securely bridge the User, the Cart, the Inventory, and the Audit system in one thread.
