# Sprint 5 — VNPay sandbox runbook

Use `integration/sprint5` in both repositories. This is a sandbox demonstration,
not approval to accept real money. Existing payment and credit rules are unchanged.

## 1. Local configuration

Run commands from `D:\ai-planning-backend`. Do not overwrite existing secret files.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
if (-not (Test-Path -LiteralPath .env.sandbox)) {
    Copy-Item -LiteralPath .env.sandbox.example -Destination .env.sandbox
}
```

The `local` profile already imports `.env`. The `sandbox` profile additionally imports
`.env.sandbox`; its values override the base file. Process environment variables have
higher priority, so clear stale payment variables or deliberately keep them consistent.
Both files are imported as **Java properties**, not executed as shell scripts:
use unquoted `KEY=value`, without `export`, interpolation or inline comments.

In `.env.sandbox`, fill only credentials supplied by VNPay **Sandbox**:

```properties
BILLING_PAYMENTS_ENABLED=true
VNPAY_TMN_CODE=<sandbox merchant code>
VNPAY_HASH_SECRET=<sandbox signing secret>
VNPAY_RETURN_URL=http://localhost:3000/billing/checkout/result
```

The committed example intentionally leaves keys blank and purchases disabled.
Never put VNPay secrets in the frontend, a NEXT_PUBLIC variable, Git or screenshots.
The sandbox profile sets both gateway endpoints to sandbox and rejects live URL overrides.
It cannot determine whether a supplied credential was actually issued for sandbox;
verify the credential email/merchant environment yourself.

## 2. Start dependencies and backend

```powershell
docker compose up -d postgres redis
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,sandbox'
```

If the wrapper cannot download Maven, use an installed `mvn.cmd` with the same arguments.
Start from the repository root so the relative configuration-file imports resolve.
Do not delete database volumes as part of sandbox setup. Flyway applies pending migrations.
Check `http://localhost:8080/actuator/health`.

## 3. Start frontend on the correct port

From `D:\ai-frontend`, follow its `.env.sandbox.example` and run `npm run dev:sandbox`.
Keep `BACKEND_URL=http://localhost:8080`: Next.js proxies authenticated API requests locally.
The tunnel is for VNPay's callback, not necessary for the local frontend's API calls.
Open `http://localhost:3000`; another port would not match the configured browser Return URL.

## 4. Create and register the backend callback URL

Choose one tunnel, in a separate terminal:

```powershell
# After installing ngrok and configuring its account authtoken:
ngrok http 8080

# OR, after installing cloudflared:
cloudflared tunnel --url http://localhost:8080
```

Register a merchant through https://sandbox.vnpayment.vn/devreg/ if needed.
Configure/send VNPay the **complete IPN URL** through its merchant integration process:

```text
https://<backend-tunnel-host>/api/v1/billing/vnpay-ipn
```

Registering only the merchant website hostname is not callback configuration.
There is no VNPAY_IPN_URL setting in this backend and no IPN field in the checkout request.
Keep `VNPAY_RETURN_URL` pointed at the frontend; it has a different purpose.
Keep backend and tunnel running, and update merchant configuration if the hostname changes.
Do not add a tunnel login page, interactive authentication or browser challenge to the IPN route.
Authentication is the gateway signature, not a USER JWT.

Check `https://<backend-tunnel-host>/actuator/health` from outside the local machine.
A plain request to the IPN route without signed parameters is expected to return an invalid
checksum response (`RspCode=97`); this checks routing only and cannot grant credits.
Do not paste real callback query strings into Slack, public logs or issue descriptions.

The localhost Return URL works only when checkout is opened on this computer. A remote
tester needs a reachable frontend URL, matching Return URL and reviewed cookie/CORS settings.
Do not expose development bootstrap accounts beyond the controlled test session.

## 5. Prepare the application

1. Sign in as ADMIN at `/admin?tab=billing`; create and activate a Credit package.
   An archived package is not purchasable. This does not require an AI provider key.
2. Sign in as a separate USER; ADMIN cannot buy packages through USER APIs.
3. Read the wallet and record its initial available/reserved balance. This initializes
   any one-time welcome bonus before the purchase, avoiding misleading balance comparisons.
4. As that USER, verify `GET /api/v1/billing/configuration` returns
   `data.paymentsEnabled=true` and `GET /api/v1/billing/packages` includes the package.
   Use Swagger with the USER bearer token or inspect the frontend's API responses.
5. Buy the package from `/billing` and use VNPay's current documented sandbox payment method.
   Use only sandbox test cards/OTP, never a real bank account.

For conflict reconciliation using QueryDr, set `VNPAY_SERVER_IP` to the merchant
server address appropriate for your sandbox setup; the example loopback value is only
a local placeholder. QueryDr uses the sandbox query endpoint and a bounded timeout.
Only configure `APP_BILLING_TRUSTED_PROXY_ADDRESSES` if an immediate proxy sanitizes
forwarded headers; do not blindly trust arbitrary X-Forwarded-For values.

## 6. Minimum acceptance checklist

| Case | Expected application result |
| --- | --- |
| Valid successful payment | Order PAID; exactly one TOP_UP ledger entry; available credits increase by the snapshotted package total; reserved credits unchanged |
| Browser Return URL alone | No credit grant; frontend waits for backend verification |
| Same signed IPN repeated | No duplicate top-up or balance increase; acknowledgement 02 after the original 00 |
| Cancellation or failure | No TOP_UP credit grant; backend-authoritative order state shown |
| Invalid signature or amount | Balance unchanged; callback rejected (97 or 04) |
| IPN missing/delayed | Pending verification is not presented as confirmed payment or invented failure |
| Checkout/network retry | Same uncertain purchase reuses its idempotency key; no second order |
| Late valid success on an expired order | Exactly-once PAID settlement under the current late-payment policy |
| Conflicting FAILED/CANCELLED then success | Signed QueryDr evidence required; unavailable query does not silently grant credits |
| Other USER requests the order/history | Owner-scoped access denied; no financial information leaked |

Use automated tests for invalid/duplicate callback fixtures; do not manually edit a database
to manufacture PAID. A successful gateway screen or HTTP 200 alone is insufficient: inspect
the IPN JSON `RspCode`, persisted order, ledger count and wallet balance.
Frontend polling timeout is only a verification timeout. Recheck the existing order rather
than immediately purchasing again. Follow VNPay's documented IPN delivery/retry behavior.

## 7. Troubleshooting and verification

- Purchases disabled: check flag, active profile, imported file location, process-variable
  overrides, restart, and `/billing/configuration` as USER.
- Backend startup rejects credentials: use real sandbox credentials, not known placeholder keys.
- Callback 404: use `/api/v1/billing/vnpay-ipn`, not a generic tutorial endpoint.
- Callback blocked/HTML: check tunnel access policies and security rules before changing code.
- Merchant says paid but app waits: inspect IPN delivery and acknowledgement; never trust the
  browser return to credit a wallet.
- Invalid checksum: verify credentials belong to the same merchant/environment. Do not print keys.

```powershell
.\mvnw.cmd '-Dtest=BillingPropertiesTest,VnpaySandboxConfigurationTest,AdminBilling*Test,*Billing*,CreditReservation*Test,VnpayPaymentGatewayAdapterTest,PaymentClientIpResolverTest,AiExecutionCreditIntegrationTest,AiExecutionWorkerTest' test
```

PostgreSQL Testcontainers tests require Docker; skipped tests are not PostgreSQL verification.
When done, set payments disabled in `.env.sandbox`, restart the backend and stop the tunnel.
Do not erase wallet/ledger/order data just to finish the test session.

References:
- https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html
- https://ngrok.com/download/windows
- https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
