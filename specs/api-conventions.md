# API conventions

## Base path

All public application APIs are under `/api/v1`. Controllers and security
configuration must use constants declared in `ApiConstant`.

## Response body

Successful responses use:

```json
{
  "data": {}
}
```

Error responses use:

```json
{
  "timestamp": "2026-07-28T07:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/example",
  "requestId": "<request-id>",
  "violations": []
}
```

Only HTTP `200` responses contain a `data` field. Error responses contain the
standard metadata above; `violations` is omitted when empty. The HTTP status
remains the authoritative status for client control flow. Responses with no
body, such as HTTP `204`, remain empty.

## Authentication failures

Protected APIs use the standard error envelope for authentication failures.

| HTTP status | Code | Meaning | Client action |
|---|---|---|---|
| `401` | `AUTHENTICATION_REQUIRED` | Credentials are missing, malformed, incorrectly signed or otherwise invalid. | Show/login route as appropriate. |
| `401` | `SESSION_EXPIRED` | A previously valid access token has expired. | Preserve any local draft, warn about unsaved form data where possible, then route to login. |
| `403` | `ACCESS_DENIED` | The authenticated user lacks permission. | Do not treat as session expiry. |
## Versioning and concurrency

- Entity IDs use UUID.
- Mutable entities use optimistic-locking versions.
- APIs that modify versioned resources should expose or accept a version/ETag.
- Draft generation and regeneration commands must use explicit idempotency keys.

## Time

Timestamps are stored in UTC. Learning-day and Daily Plan calculations must use
the authenticated USER's IANA time zone stored in `UserProfile`; presentation
conversion belongs at the API/client boundary.
