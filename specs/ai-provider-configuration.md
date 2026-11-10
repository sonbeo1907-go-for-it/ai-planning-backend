# AI Provider Administration

US-ADM-01 separates provider identity, credential metadata, and purpose-specific
model settings. Only an authenticated `ADMIN` may use endpoints under
`/api/v1/admin/ai-providers` and `/api/v1/admin/ai-provider-configs`; a `USER`
receives `403 FORBIDDEN`.

## Domain boundary

- `AiProvider` registers an immutable uppercase code, display name, HTTPS API
  root, protocol, credential strategy, and lifecycle state.
- `AiProviderCredential` belongs to one provider and stores a label,
  `secretRef`, priority, and lifecycle state.
- `AiProviderConfig` binds one provider and model to an `AiPurpose`, with
  timeout, token limits, temperature, and default selection.

The first protocol adapter is `OPENAI_COMPATIBLE`. Both OpenAI and DeepSeek can
be registered through this adapter when their Chat Completions API root is
configured. Provider codes are data, not Java enums, so another compatible
provider does not require a new domain type.

## Secret boundary

Raw API keys are never accepted by the provider administration API. Credential
records store references in this form:

```text
env:OPENAI_API_KEY
env:DEEPSEEK_API_KEY
```

Set referenced variables through the deployment secret mechanism or a local
untracked `.env` file. ADMIN responses may return the reference so it can be
edited, plus `secretConfigured` and a masked suffix. They never return the
resolved value. Application logs and audit records must not contain resolved
secrets, provider response bodies, or request bodies.

## Credential selection

The first strategy is `PRIORITY`. Among enabled, non-archived credentials of the
selected provider, the highest numeric priority whose reference resolves is
selected. A tie is resolved by creation order. Selection never crosses the
provider boundary, so this is not automatic provider failover.

A credential used as the last enabled credential of a default provider cannot
be disabled or archived until another credential is enabled.

## Purpose configuration

Configurations are scoped to one purpose:

- `DOCUMENT_EXTRACTION`
- `ROADMAP_GENERATION`
- `DAILY_PLAN_GENERATION`
- `DAILY_PLAN_REVIEW`

At most one active configuration is the default for each purpose. The first
enabled configuration for a purpose must be created as its default. Selecting
a new default enables it and replaces the previous default transactionally.
The provider must be enabled and have enabled credential metadata. There is no
automatic provider failover.

## Test Connection

`POST /api/v1/admin/ai-provider-configs/{configId}/test-connection` performs one
request and never retries. It uses the exact provider, selected credential,
model, and timeout represented by the configuration. The protocol adapter sends
a minimal fixed, non-personal Chat Completions request with streaming disabled
and a one-token output limit.

The provider response body is discarded. The API returns only success/failure,
latency, provider and model identity, selected credential metadata, a sanitized
failure category, and a generic message.

## Endpoint policy

Production provider URLs must use HTTPS and cannot contain URL credentials,
queries, or fragments. Set `AI_ALLOW_INSECURE_HTTP_PROVIDER_BASE_URLS=true` only
for controlled local mocks or tests. HTTP redirects are not followed.

Providers, credentials, and configurations are archived rather than hard
deleted. A provider with active configurations cannot be archived, and a
provider used by a default configuration cannot be disabled.

## Audit and concurrency

`AuditLog` is authoritative for the ADMIN actor. Mutations and connection tests
record actor ID, actor email, action, target type, target ID, and request ID.
Secrets and personal learning content are excluded.

Mutations require the last observed entity `version`. A stale version returns
`409 CONCURRENT_MODIFICATION`.
