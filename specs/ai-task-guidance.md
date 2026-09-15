# AI Task Detail guidance specification

## Status

Proposed and revised after completion of `task-step-decomposition.md`.

The backend already provides version-owned Daily Plan Items, persisted Task
Steps, runtime Task Step completion, Learning Unit linkage, and AI-generated
Task Step decomposition. Task Steps are therefore the one canonical executable
checklist for a Daily Plan Item.

This specification adds optional AI guidance that explains how to carry out
those existing Task Steps and supplies provenance-aware references. It does
not create a second checklist, replace Task Steps, or change learning progress.

## Improvised user story

As a `USER`, I want to open a Daily Plan Item and receive focused AI guidance
for each existing Task Step, so that I understand how to execute my checklist
and can find relevant references without AI changing my plan or progress.

For a valid simple task without Task Steps, the USER may still request concise
task-level guidance and references.

## Relationship to the original story

The original story requires a Task Detail view containing:

- a short description;
- a small action checklist;
- relevant material or links;
- a `Gợi ý chưa xác minh` label for links introduced by AI outside the
  original learning material.

Task Step decomposition now owns the checklist requirement. This story owns
the explanation and reference requirements:

```text
Task Step decomposition decides WHAT actions exist.
Task Guidance explains HOW to perform those actions.
Task Step completion records WHAT the USER actually did.
Weak Topic mastery remains a separate Quiz-based decision.
```

## Scope and actors

- An authenticated `USER` may generate and view guidance only for Daily Plan
  Items owned by that USER.
- AI guidance is optional. Existing Daily Plan Items and Task Steps remain
  usable when AI is unavailable.
- `ADMIN` manages provider configuration only and has no access to Task
  Guidance, personal Task Steps, prompts, references, or learning content.
- This story does not apply, insert, reorder, delete, or complete Task Steps.
- This story does not create ProgressEntry records or change Weak Topic state.

## Domain boundaries

```text
DailyPlan
`-- DailyPlanVersion
    `-- DailyPlanItem -> optional Learning Unit
        |-- persisted Task Steps (the executable checklist)
        `-- Task Guidance
            `-- Task Guidance Revision
                |-- task-level summary
                |-- guidance for exact existing Task Steps
                `-- provenance-aware references
```

### Daily Plan Item

- Remains the scheduled and progress-bearing task.
- Belongs to one exact `DailyPlanVersion`.
- May reference one Learning Unit from the associated RoadmapVersion.
- Owns the persisted Task Steps displayed as the executable checklist.
- Is never overwritten by Task Guidance generation or regeneration.

### Task Step

- Remains the canonical executable action beneath a Daily Plan Item.
- Its title, planned guidance, order, estimate, required flag, and runtime
  completion keep the meaning defined by `task-step-decomposition.md`.
- Its existing optional `guidance` is concise planned content. AI Task Detail
  guidance may expand on it but never replaces it.
- Only the explicit Task Step completion endpoint changes its runtime checkbox
  state.

### Task Guidance

- Belongs to one USER-owned Daily Plan Item and exact DailyPlanVersion.
- Is advisory content, not planned content, an approval decision, or learning
  progress.
- Has one or more immutable revisions when generation is repeated.
- Every generated revision starts as `DRAFT`.
- The latest non-archived revision is the default revision shown by the UI.

### Task Step Guidance

- Belongs to one Task Guidance revision.
- References an exact stable Task Step ID captured in that revision's context.
- Explains how to perform that Task Step and what observable result it should
  produce.
- May contain tips, cautions, prerequisites, and relevant references.
- Has no completion state and is not an alternative Task Step.
- Cannot be applied to, merged into, or substituted for a persisted Task Step
  in this story.

### Task-level fallback guidance

A Daily Plan Item may legitimately contain no Task Steps. In that case, a
guidance revision may contain:

- a concise objective;
- a short execution description;
- one recommended starting action expressed as advisory text;
- zero or more references.

The starting action is not persisted as a Task Step. Adding a Task Step remains
an explicit DRAFT DailyPlanVersion editing operation outside this story.

## Reference provenance

Every reference has exactly one provenance type:

- `MATERIAL`: points to an owner-owned Material that was supplied in the
  generation context.
- `LEARNING_SOURCE`: points to an owner-owned LearningSource that was supplied
  in the generation context.
- `ROADMAP_CONTEXT`: points to the relevant owner-owned Roadmap Learning Unit
  without claiming an external source.
- `UNVERIFIED_EXTERNAL`: an HTTP(S) URL introduced by the AI provider and not
  derived from supplied owner-owned content.

The persistence and API representation must distinguish `MATERIAL` from
`LEARNING_SOURCE`; one ambiguous source UUID must not be interpreted against
both tables.

Only `UNVERIFIED_EXTERNAL` receives the USER-facing badge:
`Gợi ý chưa xác minh`.

Whether a reference is unverified is derived from its provenance type. A
separate mutable `unverified` value must not be allowed to contradict that
type.

## Generation context

The backend builds a bounded context from:

- the exact Daily Plan Item title, description, category, status, and planned
  minutes;
- its exact DailyPlanVersion;
- the ordered Task Steps and their planned-content entity versions;
- each Task Step's title, concise planned guidance, order, estimate, and
  required flag;
- the linked Learning Unit, parent Topic, and parent Milestone when available;
- owner-owned RoadmapSource, Material, or LearningSource content relevant to
  that Learning Unit when it is available and ready.

The backend must not send unrelated Roadmap content, full progress history, or
all owner-owned sources. Context selection is bounded by exact relationships
and token limits.

Uploaded text, extracted document content, Task Step text, task text, and
Roadmap text are untrusted data. They are delimited as data and never treated
as system, developer, tool, or provider instructions.

## Guidance requirements

When the Daily Plan Item has Task Steps, a valid Task Guidance revision
contains:

- a concise task objective;
- a short task-level execution summary;
- the exact DailyPlanVersion and DailyPlanItem identifiers used as context;
- a context snapshot containing the ordered Task Step IDs and their
  planned-content entity versions;
- one Task Step Guidance entry for every Task Step included in the snapshot;
- zero or more task-level references.

Each Task Step Guidance entry contains:

- the exact Task Step ID;
- concise execution instructions;
- an observable expected result;
- optional tips, cautions, or prerequisites;
- zero or more references.

Guidance must:

- remain within the scope of the parent Daily Plan Item and linked Learning
  Unit;
- fit the intent and planned time of the referenced Task Step;
- avoid merely repeating the Task Step, Daily Plan Item, Learning Unit, or
  Topic title;
- avoid inventing another curriculum outcome;
- avoid pretending to modify code, submit work, browse a website, or complete
  learning on the USER's behalf;
- use the USER-facing locale when available.

The system generates guidance for all current Task Steps in one bounded AI
request. Expanding an individual step in the frontend must not automatically
create a separate provider request.

## Reference and link safety

- The first implementation does not fetch, crawl, preview, execute, or test
  AI-provided external URLs.
- External URLs must use `https`, or `http` only when explicitly allowed by the
  deployment policy. Other schemes are rejected.
- Credentials, embedded authentication, local filesystem paths, loopback
  addresses, and literal private-network addresses are rejected.
- URL labels and display text are sanitized before rendering.
- Frontend external links open with `noopener` and `noreferrer`.
- Link existence, correctness, safety, and licensing are not implied by an
  `UNVERIFIED_EXTERNAL` reference.
- A Material or LearningSource reference is accepted only when the referenced
  resource is owner-owned, available for normal use, and was included in the
  exact generation context.
- Source-backed references expose stable resource IDs and optional safe page,
  section, or excerpt locators, never storage paths.
- A provider-returned source ID is treated as an untrusted claim and must be
  validated against the allow-list supplied to that request.

## State, revision, and stale-context rules

```text
No Guidance --generate--> DRAFT revision 1

DRAFT revision 1 --regenerate--> SUPERSEDED revision 1
                                  + DRAFT revision 2

DRAFT or SUPERSEDED --archive--> ARCHIVED
```

- Revision content is immutable after persistence.
- Regeneration creates a new revision and preserves every earlier revision.
- At most one revision per Task Guidance root is the latest `DRAFT` revision.
- `SUPERSEDED` means a newer revision exists; it does not delete or invalidate
  historical content.
- Archiving hides a revision from the normal view but does not delete it.
- A revision belongs to the exact item/version and Task Step context it
  analyzed. It never moves automatically to another DailyPlanVersion.
- Guidance generation never changes Task Step content or completion state.
- An `ACTIVE` DailyPlanVersion may generate and view advisory guidance because
  guidance does not mutate its frozen planned content.

For a DRAFT version, Task Steps can change after generation. A guidance
revision is `STALE` for display purposes when the current ordered Task Step IDs
or their entity versions differ from the stored context snapshot.

- A stale revision remains readable as history.
- Stale guidance must not be silently mapped to replacement Task Steps.
- Regenerating against the new Task Step snapshot creates another revision.
- `STALE` may be derived rather than stored as a mutable revision state.
- ACTIVE-version Task Step content is immutable, so an existing revision's
  mapping remains stable after activation.

## Progress and Weak Topic independence

- Viewing guidance does not complete a Task Step.
- Completing the actions described by guidance does not automatically complete
  a Task Step.
- Only the USER's explicit Task Step completion action changes the checklist
  state.
- Task Step Guidance has no runtime progress state of its own.
- Completing all Task Steps does not by itself record a Learning Unit outcome;
  parent-task outcome rules remain authoritative.
- Generating, viewing, or regenerating guidance does not create ProgressEntry,
  update Roadmap progress, or create, reopen, review, or master a Weak Topic.
- A Weak Topic may target only an exact Learning Unit.
- Only the dedicated Weak Topic mastery-check rules may change a Weak Topic to
  `MASTERED`.

## Asynchronous AI execution

- Guidance generation uses `AiExecution` rather than holding the HTTP request
  open for provider latency.
- Add purpose `TASK_GUIDANCE_GENERATION`.
- Add target type `DAILY_PLAN_ITEM`; the target ID is the exact item.
- Add result type `TASK_GUIDANCE_REVISION`.
- Success references the created revision by result ID.
- Only one QUEUED or RUNNING guidance execution may exist for the same item and
  purpose, regardless of whether competing requests use different idempotency
  keys.
- An idempotency key makes a repeated request return the same execution.
- Resource-level concurrency protection prevents different keys from creating
  multiple active executions for the same item.
- No automatic provider failover is introduced by this story.
- Provider failure changes only the AiExecution; the Daily Plan, Task Steps,
  progress, and earlier guidance revisions remain unchanged.
- Failure responses never contain prompt content or raw provider output.

## Proposed API

Exact endpoint strings must be added to `ApiConstant` during implementation.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance` | Return the latest revision and paginated revision summaries |
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/{revisionId}` | Read one exact guidance revision |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/generate` | Queue initial guidance generation |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/regenerate` | Queue a new revision against the current Task Step snapshot |
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/guidance/execution/current` | Recover the current active generation after modal close or reload |
| `GET` | `/api/v1/ai-executions/{executionId}` | Poll owner-scoped execution status |

The generation request contains an idempotency key and may contain one bounded
USER adjustment instruction for regeneration. The adjustment is untrusted
data. Requests never accept an owner ID, replacement Task Steps, completion
state, or replacement Daily Plan content.

Every repository lookup scopes plan, version, item, revision, Task Step,
Material, LearningSource, and actor through the authenticated owner. Foreign or
mismatched nested IDs return `RESOURCE_NOT_FOUND` without disclosing resource
existence.

## Response requirements

A Task Guidance revision response contains:

- guidance root and revision IDs;
- exact DailyPlanVersion and DailyPlanItem IDs;
- revision number and state;
- whether the revision is latest and whether its context is stale;
- concise objective and task-level summary;
- ordered Task Step Guidance entries keyed by exact Task Step IDs;
- task-level and step-level references with discriminated provenance;
- generation time;
- the ordered Task Step context snapshot or an opaque context fingerprint
  sufficient for stale detection.

Responses never include prompts, provider raw output, provider secrets, secret
references, raw source excerpts, storage paths, or another USER's identifiers
or content.

## Strict AI output validation

The generated JSON schema is closed and bounded. The backend validates:

- required objective and task summary;
- exact DailyPlanVersion and DailyPlanItem scope;
- the returned Task Step ID set against the supplied allow-list;
- exactly one guidance entry per supplied Task Step when Task Steps exist;
- absence of Task Step Guidance entries when the item has no Task Steps;
- instructions and expected-result length and content bounds;
- no unknown fields;
- reference count and allowed provenance types;
- Material and LearningSource IDs against their separate supplied owner-scoped
  allow-lists;
- safe external URL syntax and schemes;
- no duplicate references after canonicalization;
- absence of instructions that attempt to mutate completion, progress,
  Roadmap, Weak Topic, or Quiz state.

Invalid output retries at most twice after the original request. No partial
guidance revision is persisted. Deterministic backend checks, not prompt
wording alone, enforce identifiers, bounds, provenance, and URL safety.

## UI behavior

- Clicking a Daily Plan Item opens an accessible Task Detail modal or page.
- The UI clearly distinguishes:
  - scheduled task information;
  - Learning Unit and parent Topic context;
  - persisted executable Task Steps and their checkboxes;
  - advisory AI guidance for each Task Step;
  - task-level and step-level references.
- Expanding a Task Step displays already-generated guidance locally and does
  not make another AI request.
- Generation displays queued, running, succeeded, and failed states and may
  continue after the modal closes.
- Reopening the modal recovers the active execution or latest persisted
  revision.
- Stale guidance is visibly labelled and is never presented as guidance for a
  newly replaced Task Step.
- Every `UNVERIFIED_EXTERNAL` link displays `Gợi ý chưa xác minh` beside that
  link, not only in a page-level disclaimer.
- Material and LearningSource references display their distinct provenance.
- Guidance has no checkbox and cannot visually appear to be a second
  executable checklist.
- Keyboard focus, Escape behavior, focus restoration, and screen-reader labels
  follow the shared modal accessibility contract.

## Failure and exception flows

- No provider configured: retain the normal Task Detail and manual Task Step
  experience.
- Provider timeout or unavailable: fail only the execution and permit an
  explicit retry.
- Invalid AI output: retry within policy, then fail without partial writes.
- Archived or unavailable Material/LearningSource: omit it from new context
  and reject a returned reference to it.
- Task Steps changed while generation was running: persist the revision against
  the captured context, return it as stale when compared with the current
  DRAFT, and never remap it silently.
- DailyPlanVersion changed while generation was running: persist against the
  originally targeted item/version only.
- Duplicate generation request: return the existing active or idempotent
  execution.
- Rejected external link: reject the AI output under the closed-schema retry
  policy; never fetch the link to test it.

## Error categories

- `RESOURCE_NOT_FOUND`
- `TASK_GUIDANCE_NOT_FOUND`
- `TASK_GUIDANCE_ALREADY_RUNNING`
- `TASK_GUIDANCE_CONTEXT_STALE`
- `TASK_GUIDANCE_INVALID_REFERENCE`
- `AI_PROVIDER_NOT_CONFIGURED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_OUTPUT_INVALID`
- `CONFLICT`
- `ACCESS_DENIED`

## Persistence considerations

- A Task Guidance root is unique per Daily Plan Item.
- Revisions use immutable version-owned persistence separate from
  DailyPlanItem and TaskStep planned fields.
- Revision numbers are unique and monotonically increasing within the root.
- Task Step Guidance rows reference the exact Task Step IDs captured in the
  revision context.
- The context snapshot preserves ordered Task Step IDs and their planned
  entity versions without duplicating personal text unnecessarily.
- References store discriminated provenance and stable owner-scoped source IDs
  where applicable. Raw document content is not copied into reference rows.
- External URLs remain untrusted display data, never callbacks or server-fetch
  instructions.
- One active-execution constraint and idempotency constraints prevent duplicate
  generation.
- Foreign-key behavior preserves activated Daily Plan history and guidance
  history.
- Hard deletion is limited to unreferenced draft data under an explicitly
  approved retention operation.

## Privacy, logging, and audit

- Task content, Task Steps, guidance, source excerpts, and references are
  personal learning data.
- Prompt context and provider response content do not enter application logs,
  audit metadata, or `AiExecution.failureMessage`.
- Provider secrets and secret references are never part of guidance records or
  responses.
- Audit metadata contains actor, action, resource type, resource ID, and
  request ID only.
- Suggested audit categories are guidance generation queued, succeeded,
  failed, regenerated, and revision archived.
- `ADMIN` cannot retrieve guidance content through operational or audit APIs.

## Required tests

### Domain and service tests

- Guidance is attached to the exact DailyPlanVersion and DailyPlanItem.
- Every Task Step Guidance entry references a Task Step from that exact item.
- Guidance generation never creates, edits, reorders, deletes, or completes a
  Task Step.
- Regeneration preserves earlier revisions and marks the former latest revision
  superseded.
- Existing USER-authored and AI-generated Task Steps remain unchanged.
- A no-step item receives only the documented task-level fallback.
- A changed DRAFT Task Step snapshot makes old guidance stale.
- Guidance remains stable for immutable ACTIVE-version Task Steps.
- Guidance operations do not create progress or change Weak Topic state.

### AI contract and security tests

- One generation request covers all current Task Steps.
- Prompt context contains only related owner-owned data.
- Personal content is treated as untrusted data.
- Invalid shapes retry at most twice and persist nothing.
- Missing, duplicate, foreign, or invented Task Step IDs are rejected.
- Cross-owner Material, LearningSource, task, and revision IDs are rejected.
- Unsafe URL schemes, credentials, local paths, and literal private-network
  targets are rejected without an outbound request.
- Every provider-introduced URL is `UNVERIFIED_EXTERNAL`.
- Material and LearningSource provenance is accepted only for the matching
  source type supplied in context.

### Repository and controller tests

- Owner USER can generate, recover, poll, read, and regenerate guidance.
- Another USER receives `RESOURCE_NOT_FOUND`.
- ADMIN receives `403 Forbidden`.
- Concurrent requests produce at most one active execution for an item.
- Daily Plan detail and guidance retrieval avoid an N+1 query per Task Step or
  reference.
- Responses and logs contain no prompt, raw provider output, source content,
  storage key, or secret reference.

### Frontend acceptance tests

- Clicking a task opens the exact Task Detail.
- Existing Task Steps remain the only interactive checklist.
- Every displayed step can reveal its corresponding AI guidance.
- Expanding steps does not issue one provider-generation request per step.
- Queued, running, recovery, failure, stale, and success states are visible.
- External suggestions display `Gợi ý chưa xác minh` per link.
- Material, LearningSource, and Roadmap context references are visually
  distinguishable.
- No guidance action silently changes task or progress state.

## Dependencies

- Versioned Daily Plan Items.
- Implemented Task Step decomposition and completion.
- ACTIVE RoadmapVersion and Learning Unit linkage for Roadmap-backed tasks.
- LearningSource, Material, and RoadmapSource ownership relationships.
- AI provider selection extended for `TASK_GUIDANCE_GENERATION`.
- AiExecution extended for `DAILY_PLAN_ITEM` and
  `TASK_GUIDANCE_REVISION`.
- Shared accessible modal and asynchronous recovery behavior in the frontend.

## Acceptance criteria

1. A USER can open one owned Daily Plan Item and see its existing ordered Task
   Steps as the only executable checklist.
2. The USER can request one asynchronous AI generation operation that produces
   focused guidance for each existing Task Step.
3. Each guidance entry references an exact Task Step and contains execution
   instructions and an observable expected result.
4. A valid item without Task Steps receives concise task-level fallback
   guidance without automatically creating Task Steps.
5. AI guidance never creates, replaces, reorders, deletes, or completes Task
   Steps and never changes Daily Plan, Roadmap, progress, Quiz, or Weak Topic
   state.
6. Material-backed and LearningSource-backed references resolve only to
   owner-owned resources of the correct type supplied in prompt context.
7. Every provider-introduced external URL is labelled
   `Gợi ý chưa xác minh` and is never fetched by the backend.
8. Regeneration creates a new DRAFT revision, preserves the previous revision,
   and associates both with their exact Task Step context snapshots.
9. Guidance generated against changed DRAFT Task Steps is visibly stale and is
   never silently remapped.
10. AI failure leaves the normal Task Detail and Task Step workflows usable.
11. Every operation is owner-scoped and unavailable to ADMIN.
12. Prompt content, source content, raw AI output, and secrets never enter logs
    or audit metadata.

## Explicitly out of scope

- A second AI-generated executable checklist parallel to Task Steps.
- Automatically adding or applying suggested Task Steps.
- Replacing, reordering, deleting, or completing Task Steps through guidance.
- Per-step provider calls triggered automatically when a UI section expands.
- Treating Task Guidance or Task Steps as a Weak Topic or mastery target.
- Quiz generation or mastery state changes from Task Guidance.
- Automatic browsing, crawling, previewing, or verification of external URLs.
- Claiming that provider-suggested URLs are correct or authoritative.
- Sharing Task Guidance with another USER.
- Provider automatic failover.
- Backend Pomodoro sessions.
