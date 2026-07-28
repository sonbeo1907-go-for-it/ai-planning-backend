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

## Versioning and concurrency

- Entity IDs use UUID.
- Mutable entities use optimistic-locking versions.
- APIs that modify versioned resources should expose or accept a version/ETag.
- Submit, request-revision and approve commands must be idempotent.

## Time

Timestamps are stored in UTC. Time-zone conversion belongs at the API/client
boundary according to the configured center time zone.
