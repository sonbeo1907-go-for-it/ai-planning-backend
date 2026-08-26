# Daily Plan specification

## Scope

An authenticated `USER` plans one learning day inside a `DailyPlan` aggregate.
`DailyPlanVersion` rows freeze plan content over time and `DailyPlanItem` rows
are the individual tasks. Manual task authoring, progress recording, and
Pomodoro focus sessions belong to `US-TSK-01-MANUAL`, `US-PLN-01-MANUAL`, and
`US-TSK-02`. AI-generated draft plans belong to `US-PLN-AI`.

This document also specifies the AI task suggestion feature (`US-TSK-AI`):
when a USER opens one task, the backend can provide a short implementation
description, a small action checklist, and reference documents/links extracted
by AI.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/daily-plans` | Create a Daily Plan and its first draft |
| `GET` | `/api/v1/daily-plans/today` | Read today's plan in the user's timezone |
| `GET` | `/api/v1/daily-plans` | List owner-scoped plans |
| `GET` | `/api/v1/daily-plans/{planId}` | Read one plan and its current tasks |
| `GET` | `/api/v1/daily-plans/{planId}/versions` | List version history |
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}` | Read one exact version |
| `POST` | `/api/v1/daily-plans/{planId}/versions` | Create/clone the next draft |
| `POST` | `/api/v1/daily-plans/{planId}/versions/generate` | Generate an AI draft (`US-PLN-AI`) |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items` | Add a manual task |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/activate` | Activate a draft |
| `POST` | `/api/v1/daily-plans/{planId}/items/{itemId}/progress` | Record progress |
| `POST` | `/api/v1/daily-plans/{planId}/items/{itemId}/pomodoro` | Record a focus session |
| `DELETE` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}` | Delete a draft task |
| `GET` | `/api/v1/daily-plans/{planId}/items/{itemId}` | Read one task detail with optional AI suggestion (`US-TSK-AI`) |
| `POST` | `/api/v1/daily-plans/{planId}/items/{itemId}/ai-suggestion` | Generate an AI task suggestion, idempotent (`US-TSK-AI`) |
| `POST` | `/api/v1/daily-plans/{planId}/items/{itemId}/ai-suggestion/regenerate` | Replace the existing AI task suggestion (`US-TSK-AI`) |

Every resource lookup is scoped through the authenticated owner. Another owner
receives `RESOURCE_NOT_FOUND`, which does not disclose resource existence.


## Task AI suggestion (US-TSK-AI)

### Purpose

When a USER opens a task, the backend returns:

- the task itself;
- an optional AI suggestion containing:
  - `shortDescription`: a short "how to do this task" text;
  - `steps`: an ordered action checklist (1–20 steps);
  - `references`: at most 10 reference entries.

### Reference semantics and the "Gợi ý chưa xác minh" badge

Each reference is one of two types:

| `referenceType` | Meaning | `verified` |
|---|---|---|
| `DOCUMENT` | One original learning document owned by the USER (a `Material` linked to the USER's Roadmap, or the GOAL `LearningSource`). | `true` |
| `LINK` | An external path proposed by the AI that is not part of the USER's original documents. | `false` |

The client must show the badge **"Gợi ý chưa xác minh"** for every reference
with `verified = false`. The backend enforces this contract:

1. `verified` is derived by the backend, never read directly from AI output.
2. A `DOCUMENT` reference is `verified` only when its `documentId` matches one
   of the original documents supplied in the AI context (owned by the USER and
   linked to the plan's Roadmap). Otherwise the response is rejected and retried.
3. Every `LINK` reference is `verified = false`.
4. URLs must use the `http` or `https` scheme, contain a host, carry no embedded
   credentials, and stay within 2048 characters.

### Generation context

The AI context contains only untrusted personal learning data and is wrapped in
`BEGIN_UNTRUSTED_PERSONAL_LEARNING_CONTEXT` / `END_UNTRUSTED_PERSONAL_LEARNING_CONTEXT`:

- the task (`title`, `description`, `category`, `plannedMinutes`);
- the linked Roadmap topic (title, description, milestone title) when present;
- the USER's goal text;
- a bounded preview of original documents linked to the plan's Roadmap
  (at most 10 documents, 6000 characters each, 24000 total).

Generation uses the `DAILY_PLAN_REVIEW` provider purpose, runs synchronously
through `AiClientService`, and retries up to three times when the AI output
fails strict schema or content validation. After three failures it returns
`AI_OUTPUT_INVALID`.

### Strict AI JSON schema

```json
{
  "shortDescription": "string",
  "steps": [{"content": "string"}],
  "references": [
    {"title": "string", "referenceType": "DOCUMENT|LINK", "documentId": "uuid|null", "url": "string|null"}
  ]
}
```

Unknown or missing fields, empty checklists, duplicate steps, more than 10
references, unsafe URLs, and `DOCUMENT` references outside the supplied context
reject the whole response.

### Idempotency and persistence

- A suggestion is stored one-to-one per `DailyPlanItem`
  (`daily_plan_item_ai_suggestions.daily_plan_item_id` is unique).
- `POST .../ai-suggestion` is idempotent: an existing suggestion is returned
  without calling the AI again. It accepts an optional `Idempotency-Key`
  header (at most 100 characters).
- `POST .../ai-suggestion/regenerate` replaces the stored content and children
  in one transaction.
- Steps and references are persisted in dedicated child tables with database
  CHECK constraints that mirror the reference rules above.

### Privacy and audit

Task text and AI suggestions are personal learning content; response DTO string
representations must redact them. Audit records contain the actor email, action
(`TASK_AI_SUGGESTION_GENERATED`, `TASK_AI_SUGGESTION_REGENERATED`), resource
type, resource ID, and request ID only.

### Required tests

- Parser: strict schema acceptance and rejection.
- Validator: `verified` derivation, URL safety, content limits.
- Generator: purpose, retry, failure after three attempts.
- Service: ownership, idempotency, regeneration, persistence.
- Controller integration: full generate/read/regenerate flow and cross-owner
  `404`.

