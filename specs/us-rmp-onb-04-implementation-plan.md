# US-RMP-ONB-04 implementation plan

## Goal

Allow a USER to name a Roadmap during onboarding, preserve that value across
resume, and safely use the title already produced by AI generation when the
USER has not supplied an authoritative title.

This plan also improves the onboarding flow where those improvements directly
support durable, understandable Roadmap creation. It does not absorb the
separate flexible-time or default-time stories.

## Branch

Backend planning branch:

```text
feature/US-RMP-ONB-04
```

The frontend implementation should later use a corresponding branch created
from the then-current frontend integration branch.

## Findings in the current implementation

1. `Roadmap.beginOnboarding` leaves `title` null.
2. `SaveRoadmapOnboardingRequest` and `RoadmapOnboardingResponse` omit title.
3. The frontend wizard collects only goal, proficiency, commitment, and
   expected duration.
4. `GeneratedRoadmapPlan` already contains an AI-generated title, but
   `AiRoadmapPersistenceService.saveGeneratedVersion` ignores it.
5. `RoadmapGenerationContext.title` is rendered as “Mục tiêu học tập”, even
   though a Roadmap name and a learning goal are different concepts.
6. The goal is also included in untrusted source documents, so prompt context
   can be duplicated and ambiguously labelled.
7. Back navigation changes only local UI state. It does not persist changes
   made on the current step.
8. Resume always opens step one instead of the first incomplete step.
9. There is no final review screen showing what will be attached to the new
   Roadmap.
10. Concurrent tabs can overwrite onboarding values because PATCH does not
    require the last observed entity version.

## Product decisions applied by this plan

### Title authority

Persist a server-controlled `RoadmapTitleOrigin`:

```text
USER
GOAL_DERIVED
AI_SUGGESTED
FALLBACK
```

- Explicit USER input always wins.
- Completing onboarding with a blank title derives a concise title from the
  goal so a manual Roadmap is immediately usable.
- The existing AI Roadmap result may replace only `GOAL_DERIVED` or `FALLBACK`.
- AI never overwrites `USER`.
- Regeneration does not rename a Roadmap that already has a USER or previously
  accepted AI title.

### AI naming mechanism

Do not introduce a separate AI endpoint or execution merely to suggest a name.
Use `GeneratedRoadmapPlan.title` from the existing Roadmap-generation
execution. This adds no provider call, token round trip, polling flow, or new
failure mode.

### Goal and title separation

Roadmap title is metadata used to identify the Roadmap. Goal is Roadmap-scoped
learning intent stored in the linked GOAL LearningSource. The AI prompt receives
both with distinct labels.

## Improved onboarding UX

Use four visible steps with text labels, not three unlabeled progress bars:

1. **Tên và mục tiêu**
   - Roadmap title input, maximum 200 characters.
   - Learning goal textarea, maximum 500 characters.
   - Explain that a blank title will be derived and that AI may improve an
     automated title when AI generation is chosen.
2. **Trình độ hiện tại**
   - Preserve the current BEGINNER/BASIC/INTERMEDIATE choices.
3. **Cam kết học tập**
   - Preserve current values in this story. Flexible 4h/6h/8h/custom values
     belong to US-RMP-ONB-05.
4. **Xác nhận**
   - Show title, goal, proficiency, daily commitment, expected duration, and
     creation mode.
   - Provide edit links/back navigation.
   - Clearly distinguish “Tạo lộ trình thủ công” from “Sinh bản nháp bằng AI”.

Navigation behavior:

- Next and Back both persist the current step before navigation.
- “Lưu và thoát” persists all current valid fields.
- Resume opens the first incomplete step, or the confirmation step when all
  fields are present.
- While the form is dirty or saving, accidental close/navigation receives a
  warning.
- A failed save keeps the USER on the same step with entered values intact.
- Buttons are disabled while one mutation is in flight.
- Focus moves to the step heading after navigation; field errors are associated
  with their inputs.

## Backend work plan

### 1. Persistence and domain

Affected files/packages:

- `entity/roadmap/Roadmap.java`
- new `entity/roadmap/RoadmapTitleOrigin.java`
- `resources/db/migration/V<next>__add_roadmap_title_origin.sql`

Changes:

- Add non-null `title_origin` after safely classifying existing rows.
- Add domain methods with explicit authority:
  - save USER onboarding title;
  - ensure a derived/fallback title during completion;
  - apply an AI suggestion only to an automated title;
  - update draft metadata as USER-authored metadata.
- Keep the existing 200-character title limit.
- Do not expose a public setter that lets services bypass authority rules.

Migration policy:

- Current local migration head is V20; V21 is the likely candidate, but choose
  the next free Flyway version after rebasing before implementation.
- Existing nonblank titles are classified conservatively as `USER` so a later
  AI execution cannot overwrite them.
- Existing null/blank onboarding titles remain temporarily classifiable as
  `FALLBACK` until completion repairs them.
- Do not rewrite title history or infer it from private content in SQL logs.

### 2. Onboarding contract

Affected files:

- `controller/onboarding/dto/SaveRoadmapOnboardingRequest.java`
- `controller/onboarding/dto/RoadmapOnboardingResponse.java`
- `service/onboarding/impl/RoadmapOnboardingServiceImpl.java`
- optionally general Roadmap response DTOs when the frontend needs origin

Changes:

- Add optional `title` to partial PATCH input.
- Add `title` and server-controlled `titleOrigin` to responses.
- Add `version` to the PATCH precondition contract, or use `If-Match`; choose
  one convention and apply it consistently.
- Normalize title independently from goal.
- Reject supplied blank and overlength titles with `VALIDATION_FAILED`.
- Preserve omitted fields during partial saves.
- During completion, guarantee a nonblank title before transitioning to DRAFT.
- Keep completion idempotent and preserve the original completion timestamp.
- Keep every query and mutation owner-scoped.

Recommended title derivation:

- Trim and collapse whitespace in the goal.
- Produce a concise deterministic title, bounded to 200 characters without
  splitting a Unicode surrogate pair.
- Never send this derivation to an AI provider.
- Use “Lộ trình từ khảo sát” only when no safe goal-derived title exists.

### 3. Correct AI generation context

Affected files:

- `service/roadmap/RoadmapGenerationContext.java`
- `service/roadmap/impl/AiRoadmapPersistenceService.java`
- `service/roadmap/impl/AiRoadmapGeneratorServiceImpl.java`

Changes:

- Replace the ambiguous context `title` field with distinct
  `roadmapTitle` and `learningGoal` fields.
- Resolve the exact owner-owned GOAL LearningSource during preparation.
- Do not duplicate that GOAL text inside the generic source-document array.
- Render title and goal under separate prompt labels.
- Keep all title, goal, and source text inside the untrusted-data boundary when
  appropriate; none becomes a system instruction.
- Continue redacting prompt/source content from logs and failure messages.

### 4. Apply the existing AI suggestion safely

Affected file:

- `service/roadmap/impl/AiRoadmapPersistenceService.java`

Changes:

- In the same transaction that saves the generated RoadmapVersion, inspect the
  current locked Roadmap title origin.
- Apply `GeneratedRoadmapPlan.title` only when origin is `GOAL_DERIVED` or
  `FALLBACK` and the generated title passes normal title validation.
- Mark an applied suggestion `AI_SUGGESTED`.
- Preserve `USER` titles even if the AI execution started before the USER edit.
- An invalid AI title does not fail otherwise valid generated Roadmap content;
  retain the existing derived/fallback title.
- Regeneration preserves `USER` and `AI_SUGGESTED` titles unless a future
  explicit rename story says otherwise.

### 5. Manual Roadmap metadata compatibility

Affected file:

- `service/roadmap/impl/ManualRoadmapServiceImpl.java`

Changes:

- Manual Roadmap creation records title origin as `USER`.
- Explicit draft metadata edits record title origin as `USER`.
- Editable copies receive a protected, non-AI-overwritable title origin.
- ACTIVE Roadmap immutability rules remain unchanged.

### 6. Documentation

Affected specifications:

- `specs/roadmap-onboarding-title.md`
- `specs/roadmap-onboarding.md`
- OpenAPI annotations on onboarding DTOs/controller

Changes:

- Merge the title refinement into the canonical onboarding field list.
- Remove ambiguity between Roadmap title and learning goal.
- Document title authority and asynchronous AI behavior.
- Do not change the fixed time choices in this story.

## Frontend work plan

Affected files/packages:

- `src/types/api.ts`
- `src/features/roadmaps/roadmap-onboarding-form.tsx`
- new focused onboarding helpers/components if needed
- new onboarding component tests

Changes:

1. Extend `RoadmapOnboarding` with `title` and `titleOrigin`.
2. Include title in local form state and PATCH payloads.
3. Refactor the wizard into four labelled steps.
4. Add the confirmation summary and edit navigation.
5. Persist before both forward and backward navigation.
6. Derive the resume step from returned durable data.
7. Track the latest returned entity version for optimistic concurrency.
8. Preserve form data after validation, network, and AI queue failures.
9. After AI generation completes, refresh Roadmap metadata so an applied AI
   suggestion appears without a hard reload.
10. Add an unsaved-change guard and accessible focus/error behavior.

Do not add a standalone AI-title request button in this story.

## Test plan

### Backend integration tests

Extend:

- `RoadmapOnboardingControllerIntegrationTest`
- `RoadmapOnboardingDtoPrivacyTest`
- migration tests

Cover:

- partial save and resume of title;
- USER title normalization and limits;
- completion with USER title;
- goal-derived title when title is omitted;
- static fallback when derivation is impossible;
- independence of titles across multiple Roadmaps;
- stale-version conflict between two tabs;
- completion idempotency;
- foreign-owner 404 and ADMIN 403;
- OpenAPI title/origin/version contract;
- no personal title/goal content in DTO `toString`, logs, or audit metadata.

### Backend AI/service tests

Extend:

- `AiRoadmapPersistenceServiceTest`
- `AiRoadmapGeneratorServiceImplTest`
- `AiRoadmapSchemaValidatorTest` where appropriate
- `ManualRoadmapServiceImpl` tests

Cover:

- prompt distinguishes current title from learning goal;
- goal is not duplicated as a generic source document;
- AI title replaces GOAL_DERIVED/FALLBACK;
- AI title never replaces USER;
- concurrent USER edit wins over an older AI execution;
- invalid/blank/overlength AI title leaves the current title intact while
  valid generated content is saved;
- regeneration does not unexpectedly rename;
- manual create/edit/copy records protected title authority.

### Frontend tests

Add tests for:

- loading and resuming title;
- first-incomplete-step selection;
- title and goal validation;
- Back/Next persistence;
- confirmation summary;
- manual and AI submission payloads;
- stale-version and ordinary validation errors;
- failed save retaining input;
- unsaved-close warning;
- keyboard focus and accessible labels.

## Pull-request breakdown

1. **PR 1 — Domain and contract**
   - migration, title origin, Roadmap methods, onboarding DTO/service, tests.
2. **PR 2 — AI title integration**
   - separate title/goal context, remove duplication, safe AI suggestion,
     manual metadata compatibility, tests.
3. **PR 3 — Frontend onboarding UX**
   - API types, four-step flow, confirmation, durable navigation, tests.
4. **PR 4 — Documentation and end-to-end verification**
   - reconcile canonical spec/OpenAPI, privacy checks, browser-to-backend flow.

PR 1 and PR 2 may share the backend feature branch but should remain separate
commits/review units. Frontend PR 3 waits for the PR 1 contract and PR 2 title
behavior to stabilize.

## Stop/go gates

### Gate 1 — Contract

Go only when title normalization, provenance, ownership, and migration policy
are approved.

### Gate 2 — Backend

Go only when onboarding and AI persistence tests prove that USER titles cannot
be overwritten.

### Gate 3 — Frontend

Go only when the OpenAPI contract is stable and resume/version-conflict behavior
is verified.

### Gate 4 — Merge

Go only when both manual and AI paths create a named DRAFT, save/resume works,
ADMIN remains denied, personal content is absent from logs/audit metadata, and
the full backend/frontend suites pass.

## Risks

- A migration/version collision with other feature branches; allocate the
  Flyway number after rebasing.
- A stale AI execution overwriting newer USER metadata; mitigate with row
  locking plus title-origin checks.
- Breaking existing Roadmap DTO consumers by inserting record fields; update
  all constructors and frontend types atomically.
- Prompt behavior changing because goal duplication is removed; preserve
  schema validation and add prompt-context tests.
- Overloading this story with time-setting changes; keep US-RMP-ONB-05 and
  US-ACC-03 separate.

## Explicitly deferred

- Flexible 4h/6h/8h/custom commitment values: US-RMP-ONB-05.
- Default-time precedence: US-ACC-03.
- AI-generated title as a separate request before Roadmap generation.
- Editing ACTIVE Roadmap metadata without creating the approved copy/version.
- Calendar availability, weekly schedule, social/sharing, and automatic
  onboarding personalization.

## Definition of done

- Every completed onboarding produces a nonblank, owner-scoped Roadmap title.
- USER-entered titles survive AI generation and regeneration.
- AI output can improve only an automated title and does so without another AI
  request.
- Goal and title are distinct throughout API, persistence, and prompt context.
- Wizard save/resume/back/confirmation behavior is durable and accessible.
- Manual creation works without AI.
- No secret or personal learning content enters logs or audit metadata.
- Full backend and frontend tests pass before merge.
