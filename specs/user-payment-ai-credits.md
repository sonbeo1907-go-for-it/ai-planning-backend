# User Payment and AI Credit Wallet Specification

Status: Proposed for the payment sprint  
Last updated: 2026-10-06  
Primary actors: USER, ADMIN, payment gateway  
Related modules: AI Provider Configuration, `AiExecution`, Audit Log

## 1. Purpose

The platform pays AI providers through system-owned provider credentials. This feature allows users to prepay for application credits and spend those credits on AI operations while preserving all existing manual learning workflows.

The first implementation uses fixed credit prices per AI purpose and optional model category. Provider input/output token usage remains an internal cost metric and is not directly exposed as a user-facing price. This gives users a predictable price and prevents provider price changes from retroactively affecting completed transactions.

## 2. Sprint goal

Allow a USER to:

- view an AI credit balance;
- select a credit package and create a payment order;
- receive credits after a payment gateway confirms payment;
- spend credits safely on AI operations;
- receive reserved credits back when an AI operation fails;
- review top-up and usage history;
- continue using manual Roadmap and Daily Plan workflows when credits are unavailable.

## 3. Product decisions required before production

The team must configure or approve the following values before production deployment:

- payment gateway and its sandbox/production environments;
- VND-to-credit conversion;
- available top-up packages and bonus credits;
- fixed credit price for each AI purpose/model category;
- promotional balance for new accounts;
- minimum and maximum top-up amounts;
- order expiration duration;
- credit expiration policy, if any;
- refund and chargeback policy;
- accounting, invoice and tax handling.

These values must be configurable. They must not be scattered as constants throughout controllers and services.

## 4. Scope

### 4.1 Must Have

- One wallet per USER.
- Active top-up package listing.
- Payment order creation.
- Payment gateway checkout/QR handoff.
- Signed webhook processing.
- Idempotent credit granting.
- Immutable credit ledger.
- AI credit reservation, settlement and release.
- User transaction history.
- Owner-scoped access.
- Administrative package and rate configuration, or equivalent initial configuration supplied by migration/application configuration.
- Audit logs for administrative changes and balance adjustments.

### 4.2 Should Have

- Promotional new-user credits.
- Paginated and filtered transaction history.
- Reconciliation for stale payment orders and credit reservations.
- Clear user-facing recovery guidance when payment or AI generation fails.

### 4.3 Out of scope

- Recurring subscriptions.
- Credit withdrawal or cash conversion.
- Credit transfer between users.
- Coupons and referral programs.
- Multiple currencies.
- Automatic card refunds.
- User-facing dynamic prices based directly on raw provider tokens.
- Peer-to-peer marketplace features.

## 5. User stories

### US-PAY-01 — View AI credit wallet

**As a** USER,  
**I want** to see my available and reserved AI credits,  
**So that** I know whether I can use AI features.

#### Acceptance criteria

- Each USER has exactly one wallet.
- The response distinguishes available credits from temporarily reserved credits.
- A new account may receive a configurable promotional credit amount exactly once.
- Available and reserved balances cannot be negative.
- A USER can only access their own wallet.
- ADMIN wallet-management permissions do not grant access to the USER's personal learning content.
- Zero credits do not block manual Roadmap, Daily Plan or Task workflows.

Suggested estimate: 3 SP.

### US-PAY-02 — Purchase a credit package

**As a** USER,  
**I want** to select a credit package and open a payment checkout,  
**So that** I can purchase additional AI credits.

#### Acceptance criteria

- Only active packages are shown to a USER.
- Each package displays its VND price, base credits, bonus credits and total granted credits.
- Monetary values are stored as integer VND amounts, never floating-point values.
- Creating an order returns the information needed to open the gateway checkout or display payment instructions.
- A new order begins in `PENDING` state.
- The order snapshots the package name, VND amount, base credits, bonus credits and total credits.
- Editing or archiving a package does not alter an existing order.
- The same order idempotency key cannot create multiple orders for the same USER.
- Configured minimum and maximum top-up limits are enforced.
- The application does not receive or store raw payment-card information.

Suggested estimate: 5 SP.

### US-PAY-03 — Confirm payment and grant credits

**As a** USER,  
**I want** my credits to appear after confirmed payment,  
**So that** I can use AI features immediately.

#### Acceptance criteria

- A verified payment gateway webhook is authoritative for payment completion.
- A browser return URL or frontend success screen never grants credits.
- Webhook processing verifies the signature, order reference, provider transaction ID, amount and currency.
- A confirmed order transitions from `PENDING` to `PAID`.
- A `PAID` transition creates exactly one immutable credit ledger entry.
- Repeated delivery of the same webhook cannot grant credits twice.
- A provider transaction ID cannot be attached to more than one successful order.
- Invalid signatures, unknown orders, mismatched amounts and mismatched currencies do not change wallet balances.
- Supported terminal order states include `PAID`, `FAILED`, `CANCELLED` and `EXPIRED`.
- The frontend may refresh a specific order status for a limited period. It must not mutate payment state locally.

Suggested estimate: 8 SP.

### US-CRD-01 — Charge AI executions using credits

**As a** USER,  
**I want** AI operations to consume a predictable number of credits,  
**So that** I can control my spending.

#### Acceptance criteria

- The frontend displays the applicable credit price before a USER confirms an AI operation.
- Before an `AiExecution` is queued, the backend atomically reserves the required credits.
- An AI execution is not created or queued if reservation fails.
- Concurrent requests cannot spend the same credits.
- A successful execution settles its reservation into a consumed charge.
- A failed, rejected or cancelled execution releases its reservation.
- Automatic retries within the same `AiExecution` do not create an additional user charge.
- A USER-initiated Regenerate request creates a new execution and a new charge.
- Every reservation and charge references its `AiExecution`.
- The applied rate is snapshotted. Later rate changes do not alter historical charges.
- Insufficient balance returns `INSUFFICIENT_AI_CREDITS` without exposing provider secrets.
- The frontend offers top-up and the existing manual workflow after insufficient-credit failure.
- If the provider does not return token usage, settlement still follows the snapshotted fixed operation price.

Suggested estimate: 8 SP.

### US-PAY-04 — View top-up and usage history

**As a** USER,  
**I want** to see how credits were added, reserved, consumed and returned,  
**So that** I can verify my balance.

#### Acceptance criteria

- History contains top-ups, promotional credits, reservations, AI usage, releases, refunds and administrative adjustments.
- Each entry contains timestamp, transaction type, credit amount, resulting balance or balance impact, status and related reference.
- Results are paginated and ordered newest first with a stable ID tie-breaker.
- The USER can filter by transaction type and date range.
- A USER can only read their own ledger history.
- Failed AI operations clearly show that reserved credits were released.
- API keys, webhook signatures, raw gateway payloads and AI provider response content are never returned.

Suggested estimate: 5 SP.

### US-ADM-PAY-01 — Manage packages and AI credit rates

**As an** ADMIN,  
**I want** to manage top-up packages and AI-operation prices,  
**So that** the platform can control its cost and revenue.

#### Acceptance criteria

- ADMIN can create, edit, activate and archive credit packages.
- A referenced package cannot be hard-deleted.
- ADMIN can configure a credit price by AI purpose and optional model category.
- At most one active rate applies to a purpose/model-category combination at a time.
- Existing orders and charges keep their original snapshots.
- Every modification records the modifying ADMIN in `AuditLog`.
- A manual balance adjustment requires a reason and creates an immutable ledger entry plus an audit record.
- USER receives HTTP 403 for administration endpoints.

Suggested estimate: 5 SP. This story may be replaced in the first sprint by migration/application configuration for packages and rates, but the runtime charging rules remain mandatory.

## 6. Business rules

### 6.1 Credit representation

- Credits are whole-number internal units represented by `long`/`BIGINT`.
- Credits are not money, are not withdrawable and cannot be transferred.
- VND amounts are positive integer amounts.
- No balance or price calculation uses `float` or `double`.
- Provider token counts and provider-estimated cost remain analytics fields on or related to `AiExecution`.

### 6.2 Wallet invariants

- One wallet exists per USER, enforced by a unique `user_id` constraint.
- `available_credits >= 0`.
- `reserved_credits >= 0`.
- Reserving `N` credits atomically decreases available by `N` and increases reserved by `N`.
- Settling `N` credits atomically decreases reserved by `N` and records usage.
- Releasing `N` credits atomically decreases reserved by `N` and restores available by `N`.
- The wallet row is a fast current-balance projection. The immutable ledger is authoritative for explaining every change.

### 6.3 Pricing

- The initial pricing model charges a fixed number of credits per AI purpose and optional model category.
- A price preview and the reservation use the same active rate selection rule.
- The server resolves the price again when reserving; it never trusts a client-submitted credit price.
- The reservation stores the applied rate ID and price snapshot.
- Provider/model changes made after reservation do not silently increase the current execution's user charge.

### 6.4 Payment state machine

```text
PENDING -> PAID
PENDING -> FAILED
PENDING -> CANCELLED
PENDING -> EXPIRED
```

- Terminal order states cannot return to `PENDING`.
- `PAID` cannot be produced by a frontend callback.
- Late valid gateway notifications follow the documented gateway policy. Any transition from an expired/failed state to paid must be explicit, tested and idempotent.

### 6.5 AI credit reservation state machine

```text
RESERVED -> SETTLED
RESERVED -> RELEASED
```

- `SETTLED` and `RELEASED` are terminal.
- Settlement and release are idempotent.
- The reservation is created in the same transaction boundary as accepting the AI request, or through a design that cannot leave a queued execution without a reservation.
- If execution creation fails after balance reservation, the reservation is rolled back or released.

### 6.6 Manual fallback

- Exhausted credits block only chargeable AI operations.
- Manual Roadmap creation, manual Daily Plan creation/editing, task execution, Pomodoro, progress recording and source management remain usable according to their existing authorization rules.

## 7. Persistence model

Names may follow existing project naming conventions, but the responsibilities and constraints below are required.

### 7.1 `credit_wallets`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `user_id` | Unique FK to USER |
| `available_credits` | BIGINT, non-negative |
| `reserved_credits` | BIGINT, non-negative |
| `version` | Optimistic-lock version |
| `created_at`, `updated_at` | UTC timestamps |

### 7.2 `credit_packages`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `code` | Stable unique business code |
| `display_name` | User-facing name |
| `price_vnd` | Positive BIGINT |
| `base_credits` | Positive BIGINT |
| `bonus_credits` | Non-negative BIGINT |
| `status` | `ACTIVE` or `ARCHIVED` |
| `version` | Optimistic-lock version |

### 7.3 `top_up_orders`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `user_id` | Order owner |
| `package_id` | Nullable historical reference if package is retained/archived |
| package snapshot fields | Code, name, price and granted credits |
| `currency` | `VND` in the initial implementation |
| `payment_provider` | Configured gateway identifier |
| `status` | Payment state |
| `idempotency_key` | Unique within the USER/order operation |
| `external_transaction_id` | Unique when present |
| `checkout_reference` | Non-secret provider reference |
| `expires_at`, `paid_at` | UTC timestamps |
| `created_at`, `updated_at` | UTC timestamps |

### 7.4 `credit_ledger_entries`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `wallet_id`, `user_id` | Owner scope |
| `entry_type` | Top-up, bonus, reserve, usage, release, refund or adjustment |
| `available_delta` | Signed BIGINT |
| `reserved_delta` | Signed BIGINT |
| `available_balance_after` | Snapshot after transaction |
| `reserved_balance_after` | Snapshot after transaction |
| `reference_type`, `reference_id` | Order, reservation, execution or adjustment |
| `idempotency_key` | Unique for the business event |
| `description` | Sanitized user-readable reason |
| `recorded_at` | Immutable UTC timestamp |

Ledger entries are append-only. Corrections are represented by compensating entries, not updates or deletes.

### 7.5 `ai_credit_rates`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `purpose` | Existing `AiPurpose` |
| `model_category` | Optional pricing category |
| `credit_cost` | Positive BIGINT |
| `status` | `ACTIVE` or `ARCHIVED` |
| `effective_from` | UTC timestamp |
| `version` | Optimistic-lock version |

### 7.6 `ai_credit_reservations`

| Column | Notes |
|---|---|
| `id` | UUID primary key |
| `wallet_id`, `user_id` | Owner scope |
| `ai_execution_id` | Unique reference to `AiExecution` |
| `rate_id` | Applied rate reference |
| rate snapshot fields | Purpose, category and credit cost |
| `reserved_credits` | Positive BIGINT |
| `charged_credits` | Set on settlement |
| `status` | `RESERVED`, `SETTLED`, `RELEASED` |
| `reserved_at`, `settled_at`, `released_at` | UTC timestamps |

## 8. Processing flows

### 8.1 Top-up flow

```text
USER selects package
  -> backend validates package and creates PENDING order snapshot
  -> gateway adapter creates checkout/QR reference
  -> frontend redirects or displays instructions
  -> gateway sends signed webhook
  -> backend verifies signature, reference, currency and amount
  -> transaction locks order and wallet
  -> order becomes PAID
  -> ledger TOP_UP entry is appended once
  -> wallet available balance increases
  -> frontend reads the authoritative order/wallet state
```

### 8.2 AI execution flow

```text
USER requests an AI operation
  -> backend resolves provider/model and active credit rate
  -> transaction locks/versions wallet and reserves credits
  -> reservation and ledger RESERVE entry are created
  -> AiExecution is created and queued
  -> worker invokes provider
  -> success: reservation SETTLED + ledger USAGE entry
  -> failure/cancellation: reservation RELEASED + ledger RELEASE entry
```

## 9. API contract outline

Exact response envelopes follow `specs/api-conventions.md`.

### 9.1 USER endpoints

```http
GET  /api/v1/billing/wallet
GET  /api/v1/billing/packages
POST /api/v1/billing/top-up-orders
GET  /api/v1/billing/top-up-orders/{orderId}
GET  /api/v1/billing/top-up-orders?page=0&size=20
GET  /api/v1/billing/transactions?page=0&size=20&type=&from=&to=
GET  /api/v1/billing/ai-prices
```

`POST /top-up-orders` requires an idempotency key and package ID. The server derives all prices and credits from the active package.

### 9.2 Payment gateway endpoint

```http
POST /api/v1/billing/webhooks/{provider}
```

This endpoint does not use USER JWT authentication. It authenticates the provider notification using the provider-specific signature scheme and replay protection.

### 9.3 ADMIN endpoints

```http
GET    /api/v1/admin/billing/packages
POST   /api/v1/admin/billing/packages
PUT    /api/v1/admin/billing/packages/{packageId}
PATCH  /api/v1/admin/billing/packages/{packageId}/status

GET    /api/v1/admin/billing/ai-rates
POST   /api/v1/admin/billing/ai-rates
PUT    /api/v1/admin/billing/ai-rates/{rateId}
PATCH  /api/v1/admin/billing/ai-rates/{rateId}/status

POST   /api/v1/admin/billing/wallets/{userId}/adjustments
```

## 10. Error codes

| Code | HTTP status | Meaning |
|---|---:|---|
| `CREDIT_PACKAGE_NOT_FOUND` | 404 | Package unavailable or not found |
| `TOP_UP_ORDER_NOT_FOUND` | 404 | Order unavailable to the actor |
| `TOP_UP_ORDER_NOT_PAYABLE` | 409 | Order is terminal or expired |
| `PAYMENT_AMOUNT_MISMATCH` | 409 | Gateway amount/currency differs from order snapshot |
| `PAYMENT_SIGNATURE_INVALID` | 401 or 400 | Provider notification cannot be authenticated |
| `PAYMENT_PROVIDER_UNAVAILABLE` | 503 | Checkout creation/query failed |
| `INSUFFICIENT_AI_CREDITS` | 409 | Wallet cannot reserve the required amount |
| `AI_CREDIT_RATE_NOT_CONFIGURED` | 503 | No applicable active price exists |
| `CREDIT_RESERVATION_CONFLICT` | 409 | Reservation is already terminal or concurrently modified |
| `CONCURRENT_MODIFICATION` | 409 | Wallet/order version conflict requiring safe retry |

Provider error content, secrets and raw webhook payloads must not be placed in these responses.

## 11. Security and audit requirements

- Store gateway secrets outside business tables using the existing secret-reference approach where possible.
- Verify signatures over the exact raw request representation required by the gateway.
- Use unique external transaction IDs and webhook event IDs for replay prevention.
- Never use source IP as the sole webhook authentication mechanism.
- Never log API keys, gateway secrets, signatures, card details or full raw payloads.
- Sanitize payment descriptions and external references before logs or responses.
- Scope every USER query by authenticated user ID at repository/service boundaries.
- Restrict package, rate and balance-adjustment endpoints to ADMIN.
- Record the modifying ADMIN, target, reason and correlation/reference ID in `AuditLog`.
- Use database transactions plus row locking or optimistic concurrency controls for wallet mutations.
- Apply rate limits to order creation and unauthenticated webhook endpoints.

## 12. Reliability and reconciliation

- Order creation and webhook processing are idempotent.
- Reservation, settlement and release are idempotent.
- A scheduled reconciliation job handles expired `PENDING` orders and stale `RESERVED` executions.
- Reconciliation must confirm execution/order state before releasing or changing credits.
- Gateway outages do not change wallet balance and do not block non-payment/manual learning features.
- Internal metrics include successful/failed checkout creation, webhook verification failure, duplicate webhook, reservation failure, settlement/release and stuck-record counts.
- Operational logs use identifiers and statuses, not personal payment information or secret payload content.

## 13. Frontend requirements

- Show available credits in a stable account/billing location.
- Show the price before confirmation of every chargeable AI action.
- Show clear `PENDING`, `PAID`, `FAILED`, `CANCELLED` and `EXPIRED` order states.
- Do not display a successful balance before the backend confirms it.
- Disable accidental duplicate order submissions while preserving idempotent retry.
- On insufficient credits, offer `Nạp credit` and the relevant manual workflow.
- A failed AI execution explains that reserved credits were returned.
- Transaction history uses Vietnamese display labels rather than raw enum values.
- Currency is formatted as VND and dates use the USER's timezone.
- Payment status refresh is bounded and stops on terminal status, timeout, unmount and authentication loss.

## 14. Required tests

### 14.1 Unit/service tests

- Wallet initialization and one-time promotion.
- Reserve, settle and release invariants.
- Insufficient balance.
- Fixed price resolution and price snapshot.
- Idempotent order creation.
- Idempotent webhook credit granting.
- Signature, amount and currency rejection.
- Duplicate external transaction/webhook event rejection.
- Administrative adjustment with a compensating ledger entry.
- AI success, failure, cancellation and automatic retry charging behavior.

### 14.2 Integration tests

- USER owner scoping for wallet, orders and ledger.
- USER receives 403 for ADMIN endpoints.
- ADMIN management actions create authoritative audit records.
- Concurrent reservations never create a negative balance.
- Concurrent duplicate webhooks grant credits once.
- Transaction rollback leaves wallet, ledger, reservation and execution consistent.
- Terminal state transitions cannot be repeated incorrectly.
- Manual workflows remain functional with zero credits.

### 14.3 Frontend tests

- Balance and package rendering.
- Top-up creation and pending-state recovery.
- Payment success only after authoritative backend status.
- Insufficient-credit recovery choices.
- Price preview for each chargeable AI action.
- History pagination/filtering and localized labels.
- Bounded status polling cleanup.

## 15. Definition of Done

- Must-Have acceptance criteria pass automated tests.
- Database constraints enforce wallet, order, transaction and idempotency invariants.
- Sandbox gateway happy path and duplicate-webhook path are demonstrated.
- AI success and failure demonstrate settlement and release.
- No log or API response exposes secrets or raw sensitive gateway content.
- Manual learning workflows work with a zero-credit wallet.
- OpenAPI and frontend contracts are updated.
- Payment configuration and sandbox setup are documented without committing secrets.
- Operational reconciliation and audit evidence are reviewable.

## 16. Recommended one-week delivery sequence

1. Wallet, ledger, package/rate configuration and migrations.
2. Gateway abstraction, sandbox order creation and signed webhook.
3. AI credit reservation integrated with `AiExecution` creation and worker terminal states.
4. Wallet, top-up and transaction-history frontend.
5. Concurrency, idempotency, security and end-to-end sandbox verification.

If the complete sequence cannot fit in the week, the team must not deploy a path that accepts real money without verified webhook idempotency and an immutable ledger. Limit the demonstration to sandbox payments until those invariants are complete.
