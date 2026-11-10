# US-RMP-ONB-05 implementation plan

## Objective

Allow a `USER` to choose a realistic Roadmap-scoped daily study commitment
instead of being restricted to 30, 60, or 120 minutes.

The supported MVP domain is:

```text
15 through 480 minutes, inclusive, in 15-minute increments
```

The value remains stored and transported as integer minutes. Each Roadmap owns
its own commitment.

## Branches

This plan is based on the merged US-RMP-ONB-04 code and is prepared on matching
branches:

```text
backend:  feature/US-RMP-ONB-05
frontend: feature/US-RMP-ONB-05
```

## Confirmed current state

1. `roadmaps.daily_commitment_minutes` already stores an integer and is
   Roadmap-scoped.
2. Migration V2 constrains it to exactly `30`, `60`, or `120`.
3. `RoadmapOnboardingServiceImpl` repeats the same fixed set in application
   validation.
4. `SaveRoadmapOnboardingRequest` advertises those three values as an OpenAPI
   enum.
5. The frontend onboarding form renders only those three buttons.
6. Save, resume, confirmation, optimistic concurrency, and owner protection are
   already available from US-RMP-ONB-04.
7. AI Roadmap generation already receives the exact stored value through
   `RoadmapGenerationContext.dailyCommitmentMinutes`.
8. Existing values 30, 60, and 120 remain valid under the new policy and need
   no data conversion.

## Scope boundary

### Included

- Roadmap onboarding quick choices: 30, 60, 120, 240, 360, and 480 minutes.
- A custom duration from 15 to 480 minutes in 15-minute increments.
- Backend, OpenAPI, frontend, and database validation using the same domain.
- Save, exit, resume, confirmation, and independent values across Roadmaps.
- Passing the exact stored commitment to AI Roadmap generation.

### Explicitly excluded

- Changing `UserProfile.defaultDailyMinutes`.
- Resolving Daily Plan time from Roadmap and profile defaults.
- Changing existing `DailyPlanVersion.availableMinutes` snapshots.
- Different commitments per weekday.
- Automatically adapting commitment from actual progress.
- Editing an ACTIVE Roadmap through an onboarding or metadata bypass.
- Changing the separate 30/60/90-day expected-duration choices.

US-ACC-03 will define profile defaults and Daily Plan budget precedence. This
story must not implement that precedence partially.

## Product decisions applied by this plan

### Canonical policy

Use one named backend policy with these constants:

```text
minimum = 15
maximum = 480
increment = 15
```

A supplied value is valid only when it is within the inclusive range and is
divisible by 15. A partial PATCH may omit the value. Onboarding completion
still requires a value.

### Custom input UX

Use six quick-choice buttons plus a `Tùy chỉnh` option.

The custom control should use separate hour and minute inputs rather than a
locale-sensitive decimal-hour input:

- hours: 0 through 8;
- minutes: 0, 15, 30, or 45;
- combined total: 15 through 480 minutes;
- when hours is 8, minutes must be 0.

The UI converts the input to total minutes before saving and displays a
normalized preview such as:

```text
270 phút (4 giờ 30 phút)
```

When a saved value is not one of the six quick choices, resume selects
`Tùy chỉnh` and reconstructs its hour/minute inputs.

### Meaning of a large commitment

The commitment is a normal planning budget or upper target. It is not actual
time studied and is not an instruction for AI to fill every available minute.
AI may create less content when that produces a more coherent Roadmap.

## Backend implementation

### 1. Centralize validation

Affected files/packages:

- `service/onboarding/impl/RoadmapOnboardingServiceImpl.java`
- optionally a small new policy in `service/onboarding/` if reuse materially
  improves readability

Changes:

- Remove `DAILY_COMMITMENT_OPTIONS`.
- Replace fixed-choice validation with a clearly named
  `validateDailyCommitment` policy.
- Accept null during partial save.
- Reject values below 15, above 480, or not divisible by 15 with
  `VALIDATION_FAILED` and a field-oriented message.
- Keep expected-duration validation unchanged.
- Do not log the submitted value.

Avoid a general-purpose duration abstraction in this story. The Roadmap policy
is small and should remain readable.

### 2. Correct the API/OpenAPI contract

Affected file:

- `controller/onboarding/dto/SaveRoadmapOnboardingRequest.java`

Changes:

- Keep `dailyCommitmentMinutes` as an optional `Integer` for partial PATCH.
- Replace the three-value OpenAPI enum with minimum 15, maximum 480, and
  multiple-of-15 documentation.
- Retain the example in integer minutes.
- Keep `entityVersion` mandatory and preserve all US-RMP-ONB-04 concurrency
  behavior.
- Keep DTO `toString()` redacted.

No endpoint path or response shape changes are needed.

### 3. Add a forward-only Flyway migration

Current migration head after US-RMP-ONB-04 is V21. The likely migration is:

```text
V22__allow_flexible_roadmap_daily_commitment.sql
```

Recheck the migration head immediately before implementation and renumber on
rebase if another branch has claimed V22.

Migration behavior:

- Do not edit V2.
- Drop only the named `ck_roadmaps_daily_commitment` constraint.
- Recreate it so null remains valid for unfinished onboarding and non-null
  values must be between 15 and 480 and divisible by 15.
- Do not rewrite Roadmap rows or Daily Plan history.
- Do not add a new column or change the integer type.

Existing constrained values are already valid under the wider rule. In a
preserved environment, still verify there are no constraint-bypassed invalid
rows before applying the new constraint. Never clamp an invalid value silently.

### 4. Preserve AI Roadmap behavior

Affected files:

- `service/roadmap/impl/AiRoadmapGeneratorServiceImpl.java`
- related prompt/context tests

Changes:

- Continue sending the exact stored integer commitment in the untrusted
  Roadmap context.
- Clarify in the trusted system prompt that it is a planning target/maximum,
  not a quota that must be filled.
- Do not allow AI output to mutate the Roadmap commitment.
- Do not introduce Daily Plan budget resolution in this story.

No changes should be required to `RoadmapGenerationContext` or its persistence
shape.

## Frontend implementation

### 1. Extract readable duration helpers

New focused module, for example:

- `src/features/roadmaps/roadmap-commitment.ts`

Responsibilities:

- expose the six quick-choice values;
- validate the 15/480/15 policy;
- convert hours and minute remainder to total minutes;
- split saved minutes back into hours and remainder;
- format normalized Vietnamese labels.

The helper should have no API or React dependency and should be unit tested.
It may later be reused by US-ACC-03, but this story must not modify profile
behavior.

### 2. Add a focused commitment control

New component, for example:

- `src/features/roadmaps/roadmap-commitment-field.tsx`

Behavior:

- render quick choices for 30 phút, 1 giờ, 2 giờ, 4 giờ, 6 giờ, and 8 giờ;
- render `Tùy chỉnh` as a mutually exclusive choice;
- show hour/minute inputs only for custom mode;
- show the normalized total immediately;
- expose one `number | undefined` value in minutes to the parent form;
- provide inline, accessible validation;
- retain the entered custom value while moving Back/Next;
- restore custom mode correctly for values such as 270.

### 3. Integrate with the four-step onboarding form

Affected files:

- `src/features/roadmaps/roadmap-onboarding-form.tsx`
- `src/features/roadmaps/roadmap-onboarding-form.test.tsx`

Changes:

- Replace the current three-button commitment block with the focused control.
- Keep expected duration as its independent 30/60/90-day choice.
- Disable Next when the supplied commitment is invalid.
- Preserve the existing PATCH payload field and optimistic `entityVersion`.
- Keep saving before Back, Next, and Save-and-exit.
- Display the normalized value on the confirmation step, not only raw minutes.
- Preserve form state after validation, network, or concurrency failures.

`src/types/api.ts` does not require a type change because the value remains an
integer.

## Tests

### Backend controller/service tests

Extend:

- `RoadmapOnboardingControllerIntegrationTest`
- `RoadmapOnboardingDtoPrivacyTest` where contract assertions are relevant

Cover:

- quick values 30, 60, 120, 240, 360, and 480;
- boundary/custom values 15, 45, and 270;
- invalid values 0, 14, 37, 481, and 540;
- omission during partial save;
- completion failure when still missing;
- save, exit/read, resume, and completion of 270;
- stale entity-version protection remains intact;
- foreign-owner not-found behavior and ADMIN denial remain intact;
- two completed Roadmaps retain independent commitments.

### Migration tests

Update `V2FoundationMigrationTest`:

- the old assertion that 45 is invalid must be removed because 45 is now
  valid;
- verify valid inserts for 15, 270, and 480;
- verify rejection for below-minimum, above-maximum, and non-increment values;
- verify null remains valid for an unfinished onboarding row.

### AI tests

Extend:

- `AiRoadmapPersistenceServiceTest`
- `AiRoadmapGeneratorServiceImplTest`

Cover:

- 270 or 480 survives preparation unchanged;
- serialized prompt context contains the exact integer;
- the trusted prompt describes the value as a maximum/target rather than a
  fill requirement;
- logs and exception messages do not include personal onboarding content.

### Frontend tests

Add/extend tests for:

- all six quick choices;
- custom 4 hours 30 minutes producing 270;
- restoring 270 as custom after resume;
- minimum, maximum, increment, and 8-hour remainder validation;
- normalized Vietnamese display;
- Back/Next persistence and confirmation summary;
- failed save retaining the custom input;
- request payload continuing to include the latest entity version.

## Acceptance-criteria traceability

| Acceptance criterion | Planned evidence |
|---|---|
| AC1 — Quick choices | Six frontend buttons and component tests |
| AC2 — Custom value | Custom hour/minute control, normalization helpers, resume test |
| AC3 — Valid limits | Frontend inline validation, backend validation, database constraint |
| AC4 — Roadmap storage | Existing Roadmap field plus controller completion/read test |
| AC5 — 4h/6h/8h | API and migration tests for 240/360/480 |
| AC6 — Independence | Two-Roadmap owner-scoped integration test |

## Pull-request breakdown

### PR 1 — Backend policy and migration

- application validation;
- OpenAPI correction;
- forward Flyway constraint migration;
- controller and migration tests.

### PR 2 — Frontend commitment control

- pure duration helpers;
- reusable Roadmap commitment field;
- onboarding integration and tests.

### PR 3 — AI semantics and final documentation

- prompt clarification and exact-value tests;
- canonical specification status update;
- end-to-end verification.

PR 1 must land before PR 2 is enabled in integration; otherwise the frontend
can submit values the backend still rejects.

## Safe merge order

1. Rebase both branches on their current `integration/sprint2-1` heads.
2. Resolve any migration-number collision and rerun migration tests.
3. Merge backend PR 1.
4. Merge frontend PR 2.
5. Merge prompt/documentation PR 3 or include it with backend PR 1 when kept as
   a small review unit.
6. Run one browser-to-database flow for 30, 270, and 480 minutes.

## Stop/go gates

### Gate 1 — Shared validation domain

Go only when frontend, API documentation, service validation, and database
constraint all use 15–480 in 15-minute increments.

### Gate 2 — Persistence

Go only when existing values need no rewrite and migration tests prove both
valid and invalid cases.

### Gate 3 — UX

Go only when custom values survive Back, exit, resume, confirmation, and a
failed request without silently reverting to a preset.

### Gate 4 — Merge

Go only when owner isolation, ADMIN denial, optimistic concurrency, prompt
privacy, backend tests, frontend tests, and the frontend production build pass.

## Rollback points

- Before the migration: revert application/frontend commits normally.
- After the migration but before wider values are stored: application rollback
  is possible only after confirming every row still belongs to the old
  30/60/120 set.
- After values such as 270 or 480 are stored: do not restore the old constraint
  without an approved data-remediation decision. Prefer rolling forward.

## Risks

- Migration V22 may collide with another feature branch.
- Frontend and backend may drift to different ranges or increments.
- Decimal-hour input would create locale/rounding ambiguity; the recommended
  hour-plus-minute control avoids it.
- Treating 480 minutes as a quota could cause bloated AI Roadmaps; prompt
  wording must define it as a target/maximum.
- Accidentally modifying profile defaults or Daily Plan fallback behavior would
  partially implement US-ACC-03 and create inconsistent precedence.
- AC6 wording may be interpreted as adding post-onboarding commitment editing.
  This plan proves independence through separate Roadmap onboardings and does
  not add an ACTIVE-Roadmap editing bypass.

## Decisions requiring approval before implementation

1. Confirm the recommended custom control: separate hours plus 0/15/30/45
   minute remainder, rather than decimal hours.
2. Confirm that US-RMP-ONB-05 changes onboarding only; post-completion Roadmap
   commitment editing is not added.
3. Confirm that Daily Plan default-resolution work remains entirely in
   US-ACC-03.

## Definition of done

- Every value from 15 through 480 in 15-minute increments is accepted through
  the UI, API, service, and database.
- Other values are rejected consistently without losing wizard state.
- 240, 360, 480, and a custom value such as 270 survive save/resume/completion.
- Commitments remain independent between Roadmaps.
- AI Roadmap generation receives the exact stored value and treats it as a
  planning target, not a quota.
- Existing Roadmap and Daily Plan history is unchanged.
- Owner authorization, ADMIN denial, audit redaction, and optimistic
  concurrency remain intact.
- Backend and frontend automated suites and the frontend production build pass.
