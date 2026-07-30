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
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed."
}
```

Only HTTP `200` responses contain a `data` field. Error responses contain only
`status`, `code` and `message`; the HTTP status remains the authoritative
status for client control flow. Responses with no body, such as HTTP `204`,
remain empty.

## Versioning and concurrency

- Entity IDs use UUID.
- Mutable entities use optimistic-locking versions.
- APIs that modify versioned resources should expose or accept a version/ETag.
- Submit, request-revision and approve commands must be idempotent.

## Time

Timestamps are stored in UTC. Time-zone conversion belongs at the API/client
boundary according to the configured center time zone.
