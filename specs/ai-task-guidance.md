# AI Task Detail guidance specification

## Status

Proposed. The current backend supports Daily Plan Items and AI-generated Daily
Plans, but it does not provide a separate, version-safe Task Detail guidance
resource containing actionable steps and provenance-aware references.

This specification builds on `task-step-decomposition.md`. It does not replace
Roadmap Learning Units or turn generated links into trusted sources.

## Improvised user story

As a `USER`, I want to open a Daily Plan Item and ask AI for focused execution
guidance, so that I receive a small actionable checklist and relevant references
without AI silently modifying my plan or presenting unverified links as facts.

## Why the original story needs refinement

The original story mixes three different concerns:

1. Viewing the persisted Daily Plan Item.
2. Generating advisory instructions and checklist steps.
3. Presenting references with different levels of provenance.

These concerns need separate state and authorization boundaries. AI output is a
draft suggestion. The USER chooses whether to apply suggested steps to an
editable Daily Plan version.

The system cannot call arbitrary AI-provided URLs "verified." A reference is
source-backed only when it points to a known owner-owned Learning Source or
Material supplied to the model. Any URL introduced by the provider is labelled
`UNVERIFIED_EXTERNAL` until a future verification capability explicitly changes
that status.

## Scope and actors

- An authenticated `USER` may generate and view guidance only for Daily Plan
  Items owned by that USER.
- AI generation is optional. The USER can still view and manually maintain
  Task Steps when AI is unavailable.
- `ADMIN` manages provider configuration only and has no access to Task Detail
  content, prompts, references, or personal Learning Sources.

## Domain boundaries

```text
DailyPlan
`-- DailyPlanVersion
    `-- DailyPlanItem -> optional Learning Unit
        |-- persisted Task Steps
        `-- Task Guidance
            `-- Task Guidance Revision
                |-- suggested steps
                `-- suggested references
```

### Daily Plan Item

- Remains the scheduled and progress-bearing task.
- Belongs to one exact `DailyPlanVersion`.
- May reference one Learning Unit from the associated active
  `RoadmapVersion`.
- Is not overwritten when Task Guidance is generated or regenerated.

### Task Guidance

- Belongs to one USER-owned Daily Plan Item and exact DailyPlanVersion.
- Is advisory content, not an approval decision and not learning progress.
- Has one or more immutable revisions.
- Generation or regeneration creates a new revision rather than overwriting an
  earlier revision.
- A generated revision starts as `DRAFT`.

### Suggested Task Step

- Is proposed execution detail scoped to the parent Daily Plan Item.
- Does not become a persisted executable Task Step until the USER explicitly
  applies it.
- Does not directly create ProgressEntry or Roadmap progress.

### Suggested reference

Every reference has an explicit provenance type:

- `LEARNING_SOURCE`: points to an owner-owned Learning Source or Material that
  was actually supplied in the generation context.
- `ROADMAP_CONTEXT`: points to the relevant Roadmap Learning Unit without
  claiming an external source.
- `UNVERIFIED_EXTERNAL`: a provider-suggested HTTP(S) URL not derived from an
  owner-owned source.

Only `UNVERIFIED_EXTERNAL` references receive the required USER-facing badge:
`Gợi ý chưa xác minh`.

## Generation context

The backend builds a bounded context from:

- the exact Daily Plan Item title, description, category, and planned minutes;
- its exact DailyPlanVersion;
- its Learning Unit, parent Topic, and parent Milestone when linked;
- owner-owned RoadmapSource/LearningSource content relevant to that Learning
  Unit, when available and ready;
- existing USER-authored Task Steps, which are authoritative and must not be
  overwritten;
- current task status only when it helps avoid irrelevant advice.

The backend must not send unrelated Roadmap content, full progress history, or
all Learning Sources merely because they share an owner. Context selection is
bounded by exact relationships and token limits.

Uploaded text, extracted document content, task text, and Roadmap text are
untrusted data. They are wrapped as data and never treated as system or tool
instructions.

## Guidance requirements

A valid Task Guidance revision contains:

- a concise objective;
- a short execution description;
- two to eight ordered suggested steps when decomposition is useful;
- zero or more references;
- optional cautions or prerequisites;
- the exact Daily Plan Item and version identifiers used as context.

Suggested steps must:

- be independently checkable actions;
- produce an observable result;
- fit within the parent task's planned time;
- remain within the Learning Unit scope when one is linked;
- avoid repeating the task, Learning Unit, or Topic title as the entire step;
- avoid pretending to modify code, submit work, or complete learning on the
  USER's behalf.

Simple tasks may return one concise action instead of artificial boilerplate.

## Reference and link safety

- The first implementation does not fetch, crawl, preview, or execute
  AI-provided external URLs.
- External URLs must use `https`, or `http` only when explicitly allowed by the
  environment policy. Other schemes are rejected.
- Credentials, local filesystem paths, loopback/private-network addresses, and
  embedded authentication information are rejected.
- URL text and labels are sanitized before rendering.
- Frontend external links open with safe browser attributes such as
  `noopener` and `noreferrer`.
- Link existence, safety, correctness, and licensing are not implied by the
  `UNVERIFIED_EXTERNAL` record.
- A Learning Source reference is accepted only when the referenced source is
  owner-owned, not archived for normal use, and was included in generation
  context.
- Source-backed references should include a stable source ID and optional page,
  section, or excerpt locator. They do not expose raw storage paths.
- The backend rejects references to another USER's materials even if the AI
  returns a syntactically valid UUID.

## State and version rules

```text
No Guidance --generate--> DRAFT revision
DRAFT revision --regenerate--> newer DRAFT revision
DRAFT revision --apply selected steps--> explicit USER application
DRAFT revision --archive--> ARCHIVED revision
```

- Generation never changes the Daily Plan Item or its Task Steps.
- If the DailyPlanVersion is `DRAFT`, the USER may explicitly apply selected
  suggestions as persisted Task Steps in that version.
- Applying suggestions is idempotent and preserves USER-authored steps.
- If the DailyPlanVersion is `ACTIVE`, its planned content is immutable.
  Guidance remains viewable and advisory; applying it requires an explicit new
  editable DailyPlanVersion according to Daily Plan version rules.
- Regeneration records another guidance revision and preserves earlier output.
- A guidance revision belongs to the exact item version it analyzed. It does
  not automatically move to a newer DailyPlanVersion.
- Completing suggested or persisted steps does not independently complete the
  Learning Unit; parent-task outcome rules from
  `task-step-decomposition.md` remain authoritative.

## Asynchronous AI execution

- Guidance generation uses `AiExecution` rather than holding the HTTP request
  open for provider latency.
- Introduce a distinct purpose such as `TASK_GUIDANCE_GENERATION`.
- The execution target is the exact `DAILY_PLAN_ITEM`.
- Success references the created Task Guidance revision.
- One active execution is permitted per item and idempotency key.
- No automatic provider failover is introduced by this story.
- Provider failure leaves the Daily Plan and manual Task Steps usable.
- Failure responses never contain raw provider output.

## Proposed API

Exact endpoint strings must be added to `ApiConstant` during implementation.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance` | Read guidance revisions for the exact item |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/generate` | Queue first guidance generation |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/regenerate` | Queue a new guidance revision |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/{revisionId}/apply` | Apply selected suggested steps to an editable version |
| `GET` | `/api/v1/ai-executions/{executionId}` | Poll owner-scoped execution status |

The apply request contains only selected suggested-step IDs and an idempotency
key. It never accepts an owner ID or a replacement Daily Plan Item payload.

Every lookup scopes plan, version, item, guidance revision, source, and actor in
the repository query. A foreign or mismatched nested ID returns
`RESOURCE_NOT_FOUND`.

## Response requirements

A Task Guidance response contains:

- guidance and revision IDs;
- exact DailyPlanVersion and DailyPlanItem IDs;
- revision number and state;
- concise objective and description;
- ordered suggested steps;
- references with provenance, display label, safe URL or source locator, and
  `unverified` boolean;
- generation time;
- whether the current version permits applying suggestions.

Responses never include prompts, provider raw output, answer keys, secret
references, storage paths, or content belonging to another USER.

## Strict AI output validation

The generated JSON schema is closed and bounded. The backend validates:

- required objective and description fields;
- step count, order, title length, uniqueness, estimates, and total time;
- exact Daily Plan Item scope;
- reference count and allowed provenance values;
- source IDs against the supplied owner-scoped context;
- safe external URL syntax and scheme;
- no unknown fields;
- no duplicated references after canonicalization.

Invalid output retries at most twice after the original request. No partial
guidance is persisted. Deterministic backend checks, not prompt wording alone,
enforce IDs, bounds, provenance, and URL safety.

## UI behavior

- Clicking a Daily Plan Item opens an accessible Task Detail modal or page.
- The UI distinguishes:
  - scheduled task information;
  - Learning Unit and parent Topic context;
  - persisted executable Task Steps;
  - AI suggestions not yet applied;
  - references.
- Generation shows asynchronous queued/running/succeeded/failed states and may
  continue after the modal closes.
- Reopening recovers the active execution or latest persisted guidance.
- The USER selects which suggested steps to apply; there is no automatic
  mutation.
- Every `UNVERIFIED_EXTERNAL` link displays `Gợi ý chưa xác minh` next to the
  link, not only in a page-level disclaimer.
- Source-backed references use a distinct label such as `Từ tài liệu của bạn`.
- Dirty USER edits require confirmation before closing.
- Keyboard focus, Escape behavior, focus restoration, and screen-reader labels
  follow the shared modal accessibility contract.

## Failure and exception flows

- No provider configured: show manual guidance/step editing and preserve task
  data.
- Provider timeout: fail only the execution; permit explicit retry.
- Invalid AI output: retry within policy, then fail without partial writes.
- Archived or unavailable Learning Source: omit it from new context and reject
  any returned reference to it.
- Active DailyPlanVersion: allow viewing guidance but reject in-place apply.
- Version changed while generation was running: persist guidance against the
  originally targeted item/version and do not apply it to the newer version.
- Duplicate apply: return the prior result without duplicating Task Steps.
- External link rejected by URL policy: reject or omit it according to the
  closed-schema validation policy; never fetch it to test validity.

## Error categories

- `RESOURCE_NOT_FOUND`
- `DAILY_PLAN_VERSION_NOT_EDITABLE`
- `TASK_GUIDANCE_NOT_FOUND`
- `TASK_GUIDANCE_ALREADY_RUNNING`
- `TASK_GUIDANCE_INVALID_REFERENCE`
- `TASK_STEP_TIME_EXCEEDED`
- `AI_PROVIDER_NOT_CONFIGURED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_OUTPUT_INVALID`
- `CONFLICT`
- `ACCESS_DENIED`

## Persistence considerations

- Guidance and revisions require version-owned persistence separate from
  DailyPlanItem planned fields.
- Suggested steps remain separate from applied Task Steps so regeneration
  cannot overwrite USER choices.
- References store provenance and stable owner-scoped source IDs where
  available. Raw document content is not duplicated into reference rows.
- External URLs are stored as untrusted display data, never as executable
  callbacks or server-fetch instructions.
- A unique revision number per guidance root and an idempotent apply constraint
  prevent duplicates.
- Foreign-key deletion behavior preserves activated Daily Plan history and
  applied Task Step lineage.
- Hard deletion is limited to unreferenced draft data under an explicitly
  approved retention operation.

## Privacy, logging, and audit

- Guidance, steps, source excerpts, and references are personal learning data.
- Prompt context and provider response content do not enter application logs,
  audit metadata, or `AiExecution.failureMessage`.
- Provider secrets and secret references are never part of guidance records or
  responses.
- Audit events contain actor, action, resource type, resource ID, and request ID
  only.
- Suggested audit categories are guidance generation queued/succeeded/failed,
  revision archived, and suggestions applied.
- ADMIN cannot retrieve guidance content through operational or audit APIs.

## Required tests

### Domain and service tests

- Guidance is attached to the exact DailyPlanVersion and DailyPlanItem.
- Generation and regeneration preserve previous revisions.
- USER-authored task fields and Task Steps are never overwritten.
- Suggestions can be applied only to an editable DRAFT version.
- Applying selected steps is idempotent and respects the parent time budget.
- Completing a suggestion alone does not change Roadmap progress.

### AI contract and security tests

- Prompt context contains only related owner-owned data.
- Personal content is treated as untrusted data.
- Invalid shapes retry at most twice and persist nothing.
- Cross-owner source IDs and task IDs are rejected.
- Unsafe URL schemes, credentials, local paths, and private-network targets are
  rejected without making an outbound request.
- Every provider-introduced URL is `UNVERIFIED_EXTERNAL`.
- Source-backed provenance is accepted only for a source supplied in context.

### Repository and controller tests

- Owner USER can generate, poll, read, regenerate, and explicitly apply.
- Another USER receives `RESOURCE_NOT_FOUND`.
- ADMIN receives `403 Forbidden`.
- Concurrent generation and apply operations remain idempotent.
- Daily Plan detail retrieval does not create an N+1 query per Task Step or
  reference.
- Responses and logs contain no prompt, raw provider output, source content, or
  storage key.

### Frontend acceptance tests

- Clicking a task opens its exact guidance.
- Loading and asynchronous recovery states are visible.
- Checklist suggestions are visually distinct from applied steps.
- External suggestions display `Gợi ý chưa xác minh` per link.
- Source-backed references display their provenance.
- Apply and discard actions require explicit USER intent.

## Dependencies

- DailyPlan and DailyPlanVersion ownership/version rules.
- Learning Unit linkage for Roadmap-backed tasks.
- `task-step-decomposition.md` for persisted executable steps.
- Learning Source/Material ownership and RoadmapSource relationships.
- AI provider selection and `AiExecution` infrastructure.
- Shared modal accessibility and async polling behavior in the frontend.

## Acceptance criteria

1. A USER can open one Daily Plan Item and request guidance scoped to that exact
   item and version.
2. Valid guidance contains a concise objective, actionable suggested steps, and
   provenance-aware references.
3. AI output is saved as a new `DRAFT` guidance revision and never overwrites
   the Daily Plan Item or USER-authored steps.
4. The USER explicitly selects suggestions to apply to an editable DRAFT
   DailyPlanVersion.
5. Source-backed references resolve only to owner-owned sources supplied in the
   prompt context.
6. Every provider-introduced external URL displays `Gợi ý chưa xác minh` and is
   never fetched by the backend in the first implementation.
7. Invalid or unsafe output is rejected deterministically and retries at most
   twice.
8. AI failure leaves manual Task Step authoring and task execution available.
9. Guidance and application operations are owner-scoped; ADMIN has no access.
10. Prompt content, source content, raw AI output, and secrets never enter logs
    or audit metadata.

## Explicitly out of scope

- Automatic browsing, crawling, or safety verification of external links.
- Claiming that provider-suggested URLs are correct or authoritative.
- Automatically applying suggestions or completing Task Steps.
- Replacing Learning Units with generated checklist steps.
- Automatic mastery, Weak Topic state changes, or quiz generation from Task
  Guidance.
- Sharing Task Guidance with other users.
- Provider automatic failover.
- Backend Pomodoro sessions.

