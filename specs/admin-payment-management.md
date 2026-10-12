# US-ADM-PAY-01 — Package, pricing and credit administration

Implemented on `feature/US-ADM-PAY-01`, building on `fix/payment-credit-integrity`.

## Scope

ADMIN manages credit packages, fixed AI prices and reasoned available-credit adjustments.
There is no hard-delete endpoint, raw ledger editor, bank refund, subscription or access to
personal learning content. Payments may remain disabled while catalog administration works.

## Package lifecycle

- Creation starts as ARCHIVED. Activation is an explicit action.
- Codes are immutable; names, integer VND price, base/bonus credits and display order are editable.
- Prices must meet configured top-up bounds. Credit totals must fit the frontend-safe integer range.
- Updates/status changes require the current entity version.
- Existing orders retain every original package snapshot; archived packages remain referenced.

## Pricing lifecycle

- Creation starts as ARCHIVED; activation applies immediately. Future scheduling is not supported.
- Categories are trimmed/uppercased; blank means the default (`null`) category.
- One database-enforced ACTIVE slot exists per purpose/category, including the null category.
- Editing creates a new rate ID instead of overwriting the old price. Editing an ACTIVE rate
  archives it and activates its replacement atomically; editing an archived rate creates an archived replacement.
- Activation previews identify the existing rate/version. Activation must name the same replacement target
  or fails with a concurrency conflict. The previous active rate is archived in the same transaction.
- Archiving the last applicable rate makes chargeable AI temporarily unavailable, not manual learning.
- Provider configurations may explicitly select a `pricingCategory`. No category is inferred from model names.
- Quotes and all chargeable AiExecution submission paths use the same resolver: exact category,
  then purpose default, never an unrelated category. Reservation ID/cost snapshots remain unchanged after repricing.
- A quote is a current preview, not a reserved price; the backend authoritatively resolves the price when reserving.

## Credit adjustments

- Exact email/UUID lookup returns billing identity/balances only and does not initialize the wallet.
- For an uninitialized wallet, the preview explicitly includes the configured one-time welcome balance.
- Adjustments lock the USER account/wallet, require a nonzero signed delta, a reason and an idempotency key.
- The request carries the expected wallet version, or null only if the preview shows no existing wallet.
- Only available credits change. Reserved credits are never edited, reclaimed or spent by an adjustment.
- The resulting balance must be nonnegative and within the frontend-safe integer range.
- Initialization, welcome bonus, adjustment ledger entry and AuditLog are committed or rolled back together.
- AuditLog records the modifying ADMIN and targets the immutable adjustment ledger entry holding the reason.
- Idempotency is scoped to ADMIN + USER. Identical retries return the original receipt before version checks;
  conflicting reuse of a key is rejected. Corrections require a new compensating adjustment.
- Frontend persists an uncertain request in per-ADMIN session storage and reuses its exact key/body after reload.

## API

All administration endpoints are under `/api/v1/admin/billing` and require ADMIN.
USER receives 403; unauthenticated requests receive 401.

| Endpoint | Behavior |
| --- | --- |
| GET /packages | Paginated package list, optional status |
| POST /packages | Create archived package |
| PUT /packages/{id} | Versioned package update |
| PATCH /packages/{id}/status | Versioned activation/archive |
| GET /ai-rates | Paginated rates, optional purpose/status |
| POST /ai-rates | Create archived rate |
| PUT /ai-rates/{id} | Create replacement; return its new ID/version |
| GET /ai-rates/{id}/activation-preview | Selected and currently active rate for its exact slot |
| PATCH /ai-rates/{id}/status | Versioned activation/archive with replacement expectations |
| GET /wallets/lookup?account= | Exact email/UUID billing-only lookup |
| GET /wallets/{userId} | Non-mutating billing wallet preview |
| POST /wallets/{userId}/adjustments | Idempotent signed adjustment; requires Idempotency-Key |

`GET /api/v1/billing/ai-prices/quote?purpose=...` is USER-only and returns rate ID,
provider configuration ID, resolved category and credit cost. Existing catalog listing APIs remain supported.
Lists use the standard PageResponse envelope, newest-first ID-tiebroken ordering and at most 100 rows.

## Migration and verification

- V31 adds provider pricing categories and normalized active-rate slots. Existing duplicate ACTIVE slots
  cause migration failure: review/resolve conflicting catalog records instead of silently changing billing.
- V32 replaces historical package/rate SET NULL foreign keys with RESTRICT. Java metadata lookup accommodates
  the different generated names of the original unnamed foreign key in PostgreSQL and H2.
- Previous migrations, financial snapshots, ledger records and AuditLog's identifiers-only API remain unchanged.
- Test coverage includes USER denial, package/order retention, price/reservation retention, default/category uniqueness,
  concurrent activation, quote resolution, idempotency, adjustment/audit rollback and races with top-up/AI reservations.
- PostgreSQL Testcontainers tests must run with Docker before production; skipped tests do not verify PostgreSQL.
- Run a registered merchant VNPay sandbox demonstration before enabling real-money payments.

## Frontend

`/admin?tab=billing` includes lazy-loaded package, pricing and adjustment sections.
Forms provide Vietnamese labels, integer validation, version-conflict recovery, duplicate-submission guards,
activation/archive confirmations and original receipt recovery. Archived rates/packages remain visible.
Provider configuration adds only the optional pricing-category input; existing provider workflows are preserved.
