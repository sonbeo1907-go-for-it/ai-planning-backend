# AI execution history and diagnostics specification

## Status

Planned for a later sprint. This story extends US-ADM-02 monitoring from
aggregate analytics into read-only, execution-level diagnostics. It does not
expand ADMIN access to personal learning resources.

## User story

As an `ADMIN`, I want to search AI execution history and inspect sanitized
operational diagnostics, so that I can investigate provider failures, latency,
token use, and reliability.

## Boundary with US-ADM-02

US-ADM-02 answers aggregate questions such as error rate, token consumption,
and performance by provider/model/operation. This story answers which execution
failed and what sanitized operational state led to that failure.

It does not provide raw application-log access. “Logs” means a structured,
append-only diagnostic event timeline with a strict field allowlist.

## Actor and authorization

- Every endpoint is restricted to `ADMIN`.
- A `USER` receives `403 FORBIDDEN`.
- Execution visibility does not authorize the ADMIN to read the execution's
  Roadmap, Daily Plan, source, quiz, guidance, progress, or AI output.
- No endpoint returns an owner identity or offers navigation to a personal
  target/result resource.

## Proposed API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/ai-executions` | Paginated, filtered execution history |
| `GET` | `/api/v1/admin/ai-executions/{executionId}` | Sanitized execution detail |
| `GET` | `/api/v1/admin/ai-executions/{executionId}/events` | Sanitized diagnostic timeline |

Supported list filters:

- `from` and `to`;
- provider or provider configuration ID;
- model;
- purpose;
- operation;
- execution status;
- failure code;
- page, size, and sort.

The API must enforce pagination, a maximum page size, an indexed sort, and a
bounded time window. It must not offer unrestricted export in the first
implementation.

## Response allowlist

The execution list/detail may return only operational metadata needed for
diagnosis:

- execution ID and entity version;
- provider/config identity, provider display name, and model;
- purpose and operation;
- target type and result type, without personal target/result IDs;
- status and attempt count;
- queued, started, completed, and updated timestamps;
- latency;
- input, output, and total token counts when provided;
- sanitized failure code and generic failure message;
- whether a result was produced, without returning result content.

The diagnostic timeline may return:

- event timestamp;
- state transition or attempt number;
- diagnostic category;
- sanitized code;
- generic message.

## Forbidden data

The API, persistence model, event timeline, application logs, and audit metadata
must never expose or copy:

- API keys, resolved secrets, or `secretRef` values;
- credential labels when they reveal a secret;
- idempotency keys;
- system prompts, user prompts, or rendered prompt context;
- uploaded-document text;
- Roadmap, Daily Plan, task, quiz, answer, progress, or guidance content;
- raw provider request/response bodies;
- raw stack traces or exception messages containing request data;
- owner IDs, email addresses, or other personal identity.

## Diagnostic events

If a new `AiExecutionEvent` model is introduced, it is append-only and belongs
to one `AiExecution`. Event creation is server-controlled. It contains only the
allowlisted operational fields above.

Expected event categories include:

- `QUEUED`;
- `STARTED`;
- `ATTEMPT_STARTED`;
- `ATTEMPT_FAILED`;
- `RETRY_SCHEDULED`;
- `SUCCEEDED`;
- `FAILED`;
- `TIMED_OUT`.

An event record is not a replacement for `AuditLog`. `AiExecution` and its
events describe system operation; `AuditLog` identifies ADMIN configuration
actions.

## Lifecycle and retention

- History is read-only through this API.
- The UI provides no edit, hard-delete, retry, or cancel action in this story.
- Archived provider configurations remain resolvable as historical labels.
- Retention duration and archival storage require team approval before
  implementation. Cleanup must never cascade-delete referenced configuration
  or audit history unexpectedly.

## Error sanitization

- Persist stable failure codes whenever possible.
- Persist only generic, pre-approved failure messages.
- Map provider payloads and low-level exceptions to sanitized categories before
  persistence.
- A request ID may be returned only if the operational logging policy confirms
  it cannot be used to cross the personal-data boundary.

## Indexing and query requirements

History queries should be supported by indexes for the accepted filter/sort
combinations, particularly creation time, status, purpose, provider config,
and failure code. Repository methods must use database pagination and
projections; they must not load all executions and filter in memory.

## Audit policy

Opening ordinary paginated history need not create one AuditLog per row.
Changes to retention, export policy, or diagnostic visibility are auditable
ADMIN configuration actions. Any future export feature requires its own story
and explicit audit requirements.

## Required tests

- ADMIN can list and filter paginated execution history.
- USER receives 403 for all history/diagnostic endpoints.
- Detail lookup returns sanitized operational metadata only.
- Unknown IDs return `RESOURCE_NOT_FOUND` without leaking personal context.
- Time-window and page-size limits are enforced.
- Filters combine correctly and use deterministic ordering.
- Archived provider configurations remain visible as historical metadata.
- Diagnostic events preserve ordering and attempt numbers.
- Secrets, prompts, personal content, owner identity, idempotency keys, target
  IDs, raw responses, and stack traces are absent from serialized responses.
- Failure sanitization is verified for timeout, authentication, rate-limit,
  invalid-output, and provider-unavailable cases.
- Query-count tests prevent per-row provider/configuration N+1 queries.

## Decisions required before implementation

- History retention period and archival mechanism.
- Maximum query window and page size.
- Whether sanitized request IDs may be displayed.
- Whether operational export is needed in a later story.

## Out of scope

- Raw application-log browsing.
- Prompt and response inspection.
- Access to personal target or result resources.
- Retrying, cancelling, or deleting executions.
- Editing execution records.

