# Daily Plan task-step decomposition specification

## Status

Backend implementation complete. V17 provides version-owned planned Task Steps
and separate runtime completion state. Owner-scoped manual CRUD, ACTIVE-version
completion, version cloning, batch-enriched Daily Plan read models,
activation-time state initialization, and structurally strict AI-generated decomposition are
implemented. Generated steps use an action enum, a source-grounded scope anchor,
and a deterministic paraphrase guard; these checks reject ungrounded evidence
without claiming perfect free-text semantic proof. The accessible Task Step
editor and checklist presentation remain frontend work.

## Problem statement

The curriculum and execution models have different responsibilities:

```text
RoadmapVersion
`-- Milestone
    `-- Topic
        `-- Learning Unit

DailyPlanVersion
`-- Daily Plan Item -> references one Learning Unit
    |-- Task Step
    |-- Task Step
    `-- Task Step
```

A Learning Unit is the smallest stable curriculum outcome used for Roadmap
progress. A Daily Plan Item is one scheduled study session for that outcome. A
Task Step is a small, ordered action that helps the USER execute the scheduled
session.

Without Task Steps, AI tends to paraphrase the Learning Unit as the Daily Plan
Item. For example:

```text
Learning Unit: Use the Java Stream API to process collections
Daily Plan Item: Practise the Java Stream API on collections
```

The Daily Plan Item is technically valid, but it does not tell the USER what to
do. Task Steps must provide observable actions such as creating an example,
comparing two approaches, running a test, or writing a short explanation.

## Scope and actors

- An authenticated `USER` may create, edit, reorder, remove, and execute Task
  Steps inside the USER's own Daily Plan.
- AI may propose Task Steps only as content in a new `DRAFT DailyPlanVersion`.
- Manual Task Step authoring remains available when AI is unavailable.
- `ADMIN` has no access to USER Daily Plans, Task Steps, step outcomes, or
  related personal learning content.

## Domain responsibilities

### Daily Plan Item

- Belongs to one exact `DailyPlanVersion`.
- May reference one Learning Unit from the Daily Plan Roadmap's active
  `RoadmapVersion`.
- Defines the scheduled session title, description, category, and total planned
  minutes.
- Remains the unit that records the final `COMPLETED`,
  `PARTIALLY_COMPLETED`, or `SKIPPED` learning outcome.
- May contain zero or more ordered Task Steps.

Simple tasks may legitimately have no steps. AI-generated tasks that combine
multiple actions must be decomposed.

### Task Step

- Belongs to exactly one Daily Plan Item and therefore one exact
  `DailyPlanVersion`.
- Has a stable identifier within that version.
- Contains a concise action title, optional guidance, an `orderIndex`, and an
  optional positive estimate in minutes.
- Is execution detail, not curriculum content.
- Must not be referenced as a Roadmap Item, Learning Unit, Weak Topic, or quiz
  curriculum target.
- Must not survive independently after its owning version is removed under an
  explicitly approved retention operation.

### Task Step outcome

Planned Task Step content and execution state are separate concerns. Checking a
step must not rewrite the versioned title, guidance, order, or estimate.

The runtime snapshot records whether the step is incomplete or complete and
when it was last changed. Step-level history is optional for the first
implementation; the existing parent `ProgressEntry` remains the authoritative
historical learning outcome.

## Version and editing rules

- Task Step content is editable only while its `DailyPlanVersion` is `DRAFT`.
- Adding, editing, deleting, or reordering a Task Step in a `DRAFT` version does
  not modify another version.
- Activating a version freezes Task Step content together with its Daily Plan
  Items.
- Execution checkboxes may change after activation, but those changes are
  stored as runtime progress rather than planned-content edits.
- Regeneration creates a new `DRAFT DailyPlanVersion`; it never overwrites Task
  Steps in an existing version.
- When a version is cloned, its Task Steps are copied with new version-local
  identifiers. Runtime completion state is not copied.
- USER-authored Task Steps are authoritative. AI may not silently replace them.
- Existing Daily Plan Items without Task Steps remain valid and require no
  destructive backfill.

## Decomposition rules

An AI-generated Task Step must:

1. Describe one concrete action that can be independently checked.
2. Begin with, or clearly imply, an action such as write, implement, compare,
   explain, solve, run, review, or summarize.
3. Describe an observable result rather than repeat or paraphrase the Daily Plan
   Item or Learning Unit title.
4. Stay within the scope of the referenced Learning Unit.
5. Avoid inventing another curriculum outcome.
6. Use the USER-facing locale when it is available.
7. Treat Roadmap text, Learning Source text, previous task text, and progress
   notes as untrusted data rather than instructions.

For example:

```text
Learning Unit: Use Optional to avoid NullPointerException

Daily Plan Item: Practise safe optional-value handling in the service layer

Task Steps:
1. Write one example using map and flatMap.
2. Compare orElse with orElseGet using a side-effecting fallback.
3. Replace one explicit null check with Optional.
4. Run the relevant unit tests and note one limitation.
```

AI should normally generate between two and eight steps for a decomposed task.
A single-step decomposition is rejected when the step only repeats the parent
task.

## Time rules

- A step estimate, when present, must be a positive whole number of minutes.
- The sum of step estimates must not exceed the parent Daily Plan Item's
  `plannedMinutes`.
- Unallocated parent time is permitted for reading, setup, transitions, and
  USER-controlled work.
- Steps do not independently consume additional Daily Plan budget.
- Splitting a task into steps must not increase the version's
  `totalPlannedMinutes`.

## Completion and progress rules

- Checking one Task Step never directly completes its Learning Unit.
- Step progress is calculated as completed required steps divided by total
  required steps.
- When some but not all required steps are complete, the parent task may be
  displayed as `IN_PROGRESS`; no completed Roadmap outcome is recorded.
- When all required steps are complete, the UI may offer to mark the parent
  Daily Plan Item `COMPLETED` or do so as one explicit atomic USER action.
- Only the parent Daily Plan Item outcome creates the existing historical
  `ProgressEntry` and updates the Learning Unit progress snapshot.
- `PARTIALLY_COMPLETED` records the USER's actual parent-task result without
  marking the Learning Unit complete.
- `SKIPPED` does not complete the Learning Unit, regardless of checked steps.
- Repeated step checkbox requests and repeated parent-outcome requests must be
  idempotent and must not double-count Roadmap progress.
- Removing a Task Step from a `DRAFT` version has no effect on historical
  `ProgressEntry` records.

This preserves the invariant that Roadmap progress is derived from actual
Learning Unit outcomes, not from the number of generated checklist rows.

## Proposed API groups

Exact endpoint constants must be declared in `ApiConstant` when implementation
begins.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/steps` | List ordered steps |
| `POST` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/steps` | Add a manual step to a draft |
| `PATCH` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/steps/{stepId}` | Edit or reorder a draft step |
| `DELETE` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/steps/{stepId}` | Remove a draft step |
| `PUT` | `/api/v1/daily-plans/{planId}/versions/{versionId}/items/{itemId}/steps/{stepId}/completion` | Idempotently set runtime completion |

Daily Plan detail responses should embed ordered steps to avoid an N+1 request
pattern. The separate list endpoint remains useful for targeted refreshes.

All lookups must scope the Daily Plan, version, item, and step through the
authenticated owner in the database query. A foreign or mismatched nested ID
returns `RESOURCE_NOT_FOUND` without disclosing whether the resource exists.

## Request and response requirements

A planned Task Step representation contains:

- `id`
- `dailyPlanItemId`
- `title`
- optional `guidance`
- `orderIndex`
- optional `estimatedMinutes`
- `required`
- planned-content `entityVersion`
- runtime `completed`
- optional `completedAt`
- optional runtime `stateVersion`

Mutation requests must not accept owner IDs, Daily Plan IDs, version IDs, or
parent item IDs in the body when those values are already fixed by the path.
Planned-content updates carry the expected `entityVersion`; deletion carries it
as a query parameter. Completion updates carry the last observed `stateVersion`.
Setting an already-current completion value remains idempotent even when the
submitted runtime version is stale.

## AI output contract

Each AI-generated Daily Plan Item adds a nested `steps` array:

```text
items[]
|-- roadmapItemId
|-- title
|-- description
|-- category
|-- plannedMinutes
`-- steps[]
    |-- title
    |-- guidance
    |-- orderIndex
    |-- estimatedMinutes
    |-- required
    |-- actionType
    `-- scopeAnchor
```

Strict server-side validation must verify:

- The existing Daily Plan Item rules still pass.
- Step fields are present only in the documented shape.
- Step titles are unique within the parent item after normalization.
- Step titles do not exactly repeat the parent Daily Plan Item, Learning Unit,
  or parent Topic title after normalization.
- Step order values can be normalized into a stable contiguous sequence.
- Step estimates obey the parent time limit.
- The number of steps is bounded.
- `actionType` is one of the documented observable actions.
- `scopeAnchor` is an exact phrase from the referenced Learning Unit context and
  is used by the step title or guidance.
- A deterministic similarity guard rejects title-only repetitions and likely
  paraphrases. This guard verifies grounded evidence; it does not claim to prove
  arbitrary natural-language semantics.

Malformed AI output follows the existing schema-retry policy: the original
attempt plus at most two retries. Failure leaves manual Daily Plan and Task Step
authoring available. No partially validated AI steps are persisted.

## UI behavior

- Selecting a Daily Plan Item opens an accessible modal or detail page showing
  its Learning Unit context and ordered Task Steps.
- The parent item and Learning Unit remain visually distinguishable from the
  checklist.
- A DRAFT version provides add, edit, delete, and reorder controls.
- An ACTIVE version provides completion controls without planned-content edit
  controls.
- Closing a dirty DRAFT editor requires an explicit discard confirmation.
- Keyboard focus enters the dialog at its heading or first meaningful field,
  remains trapped while open, and returns to the trigger when closed.
- Empty, loading, generation, failure, partial-progress, and completed states
  are visually distinct.
- Internal enum values are mapped to localized USER-facing labels.

## Validation and error categories

- `RESOURCE_NOT_FOUND`: the owner-scoped Daily Plan, version, item, or step is
  unavailable or nested IDs do not match.
- `DAILY_PLAN_VERSION_NOT_EDITABLE`: planned step content was mutated outside a
  DRAFT version.
- `DAILY_PLAN_VERSION_NOT_ACTIVE`: runtime completion was submitted for a
  version that is not executable.
- `TASK_STEP_INVALID`: title, guidance, order, required flag, or estimate is
  invalid.
- `TASK_STEP_TIME_EXCEEDED`: step estimates exceed parent planned minutes.
- `AI_OUTPUT_INVALID`: generated steps fail strict validation after retries.
- `CONFLICT`: an optimistic-lock or incompatible state transition failed.
- `ACCESS_DENIED`: the authenticated role is not `USER`.

## Persistence considerations

- Planned Task Steps require their own version-owned storage and must not be
  serialized into logs, audit metadata, or unrelated plaintext fields.
- Runtime step completion requires separate storage from planned Task Step
  content.
- The database should enforce parent ownership indirectly through foreign keys
  and enforce unique order positions per Daily Plan Item where practical.
- Expected planned-content and runtime-state versions protect step edits and
  checkbox updates from stale-client overwrites. Collection reordering is also
  serialized through the owning Daily Plan Version lock.
- Foreign-key deletion behavior must preserve existing Daily Plan and Roadmap
  progress history. A Task Step must never cascade-delete a parent
  `ProgressEntry`.
- Hard deletion is limited to unactivated DRAFT content under existing Daily
  Plan retention rules. Activated-version content is historical.

## Privacy and audit

- Task titles, guidance, Learning Unit context, checkbox state, and AI output are
  personal learning data.
- Application logs and audit metadata must not contain that content.
- Audit events contain actor identity, action category, resource type, resource
  ID, and request ID only.
- Suggested audit categories are `TASK_STEP_CREATED`, `TASK_STEP_UPDATED`,
  `TASK_STEP_DELETED`, and `TASK_STEP_COMPLETION_CHANGED`.
- High-frequency completion events may use a dedicated progress history rather
  than verbose audit metadata, subject to the platform audit policy.

## Required tests

### Unit tests

- Reject empty, normalized-duplicate, overlong, and exact parent-title
  repetitions.
- Reject invalid estimates and totals above parent planned minutes.
- Normalize step order deterministically.
- Prevent a checked step from directly completing a Learning Unit.
- Derive partial checklist progress correctly.

### Service and repository tests

- Enforce USER ownership for every nested-resource operation.
- Reject cross-plan, cross-version, and cross-item identifiers.
- Reject planned-content mutation after activation.
- Preserve USER-authored steps during regeneration of another version.
- Make completion updates idempotent.
- Preserve parent `ProgressEntry` history when draft steps are removed.
- Fetch steps with Daily Plan details without N+1 queries.

### AI contract tests

- Accept valid actionable steps.
- Reject title-only paraphrases of the parent task or Learning Unit.
- Reject steps outside the supplied Learning Unit context.
- Reject over-budget and oversized decompositions.
- Retry malformed output no more than twice.
- Preserve the manual fallback when the provider is unavailable.

### Controller and security tests

- USER owner receives successful CRUD and completion responses.
- Another USER receives `RESOURCE_NOT_FOUND`.
- ADMIN receives `403 Forbidden`.
- Responses never expose another owner's identifiers or content.

## Dependencies

- Versioned Daily Plan Items.
- ACTIVE RoadmapVersion and Learning Unit linkage.
- Existing parent-task `ProgressEntry` and Learning Unit progress projection.
- AI provider selection for `DAILY_PLAN_GENERATION`.
- Frontend accessible modal or detail-page infrastructure.

## Explicitly out of scope

- Adding another Roadmap hierarchy level beneath Learning Unit.
- Treating Task Steps as Weak Topics or quiz curriculum targets.
- Automatically restructuring a Roadmap from step completion.
- Automatic mastery scoring.
- Knowledge maps.
- Sharing or collaborative checklists.
- Backend Pomodoro sessions.
- Allowing AI to overwrite USER-edited steps in place.

## Acceptance criteria

1. A USER can view actionable ordered steps beneath a Daily Plan Item.
2. A USER can manually manage steps in a DRAFT DailyPlanVersion.
3. AI-generated Daily Plan drafts contain concrete steps rather than only
   paraphrasing Learning Unit titles.
4. Regeneration creates another version and preserves existing USER content.
5. Step estimates remain within the parent task and daily budgets.
6. Checking a step updates checklist progress without independently completing
   or double-counting Roadmap progress.
7. The final parent-task outcome remains the only operation that records the
   Learning Unit outcome.
8. Existing tasks without steps continue to work.
9. Every operation is owner-scoped and unavailable to ADMIN.
10. Personal content and AI output do not enter logs or audit metadata.
