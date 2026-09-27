# US-ACC-03 implementation plan

## Objective

Implement one consistent study-time budget model across the account profile,
Roadmap, Daily Plan versions, AI generation, and dashboard.

The resolution order is:

```text
explicit per-day override
    -> selected Roadmap dailyCommitmentMinutes
        -> UserProfile.defaultDailyMinutes
            -> system fallback of 60 minutes
```

The resolved value is stored once in
`DailyPlanVersion.availableMinutes`. Later edits to profile or Roadmap defaults
must not rewrite an existing version.

## Branches and baseline

This plan is based on US-RMP-ONB-04 and US-RMP-ONB-05 already merged into
`integration/sprint2-1`.

```text
backend:  feature/US-ACC-03
frontend: feature/US-ACC-03
```

The source requirements are:

- `D:/requirement/onboarding-and-study-time-user-stories.txt`, US-ACC-03;
- `specs/study-time-default-resolution.md`;
- `specs/flexible-roadmap-daily-commitment.md`.

## Confirmed current state

### Already usable

1. `UserProfile.defaultDailyMinutes`, `Roadmap.dailyCommitmentMinutes`, and
   `DailyPlanVersion.availableMinutes` already exist as separate integer
   fields.
2. Roadmap commitments already accept 15 through 480 minutes in 15-minute
   increments after migration V22.
3. `CreateDailyPlanRequest.availableMinutes` is already optional.
4. AI context and server-side AI validation already consume the persisted
   `DailyPlanVersion.availableMinutes` value.
5. Creating a user-edited or AI-regenerated version copies the previous
   version's budget instead of consulting a changed profile.
6. Profile, Roadmap, Daily Plan, and report endpoints are USER-only and use
   owner-scoped repositories.

### Gaps to correct

1. Profile DTOs accept 1 through 1440 minutes and do not enforce a 15-minute
   increment.
2. `user_profiles` has the same outdated 1-through-1440 database constraint.
3. Profile setup and editing expose only fixed 30/60/120-minute controls.
4. Daily Plan creation falls directly back to 60 when `availableMinutes` is
   absent. It ignores both the selected Roadmap and the profile.
5. A null `roadmapId` currently auto-selects the most recently updated ACTIVE
   Roadmap, despite the frontend presenting that choice as “Không liên kết”.
6. Daily Plan request validation accepts 1 through 1440 and does not enforce
   15-minute increments.
7. `DailyPlanVersion.create` and response builders contain independent magic
   60-minute defaults, so precedence is not owned by one policy.
8. The Daily Plan create modal always sends the profile default as though it
   were an explicit daily override. The backend therefore cannot distinguish
   “use fallback” from “override this day”.
9. Roadmap summary responses do not expose `dailyCommitmentMinutes`, so the
   frontend cannot explain the proposed Roadmap-derived value.
10. Dashboard chart targets always use the profile default, even for days
    that already have a concrete Daily Plan version.
11. The dashboard has no explicit Roadmap context when several ACTIVE
    Roadmaps exist.

## Product and technical decisions

### One canonical duration domain

All user-entered study-time budgets use this domain:

```text
minimum = 15 minutes
maximum = 480 minutes
increment = 15 minutes
system fallback = 60 minutes
```

Create one backend value policy, for example `StudyTimeBudgetPolicy`, containing
the constants and validation. Controllers may retain Bean Validation for range
feedback, but service/domain validation remains authoritative for the
15-minute increment.

On the frontend, extract the ONB-05 duration helpers and control into a shared,
configurable study-duration component. Roadmap onboarding, profile setup,
profile editing, and per-day override must not maintain independent ranges.

### Null means “resolve”, not “60”

For `CreateDailyPlanRequest.availableMinutes`:

- a supplied valid value is an explicit daily override;
- `null` invokes server-side resolution;
- the backend always returns the final resolved value;
- the frontend may preview the likely fallback but never becomes the authority.

### Roadmap selection must be explicit

When `roadmapId` is null, create an unlinked manual Daily Plan and continue to
the profile fallback. Do not silently attach the latest ACTIVE Roadmap.

When `roadmapId` is present, load it with `roadmapId + ownerId`, validate its
state, and use its commitment before consulting the profile.

### Persist the snapshot once

The resolver returns both the value and its source:

```text
EXPLICIT_DAY
ROADMAP
PROFILE
SYSTEM_FALLBACK
```

Only the integer value needs to be stored in `DailyPlanVersion`; the source is
useful for response/UI explanation but is not required as a new database
column. Audit and application logs must not include the selected personal
scheduling value.

`DailyPlanVersion.create` should require a resolved, validated value. It must
not independently replace null with 60. The 60-minute fallback belongs only in
the resolver.

### Editing the budget before regeneration

Allow the owner to change `availableMinutes` only on a DRAFT Daily Plan
version. Add an owner-scoped operation such as:

```text
PATCH /api/v1/daily-plans/{planId}/versions/{versionId}/budget
```

The request contains `availableMinutes` and the observed entity version. The
operation validates the common duration domain and optimistic concurrency.
ACTIVE and SUPERSEDED versions remain immutable.

AI generation/regeneration then reads the persisted DRAFT budget. This avoids
putting transient request data inside `AiExecution`, survives asynchronous
worker execution, and preserves the prior version when regeneration creates a
new DRAFT. If the UI wants a new budget, it saves the DRAFT budget first and
queues regeneration second.

### Dashboard context

Extend the existing dashboard endpoint with an optional owner-scoped
`roadmapId` query parameter:

```text
GET /api/v1/reports/dashboard?roadmapId={roadmapId}
```

Resolution for each seven-day chart point is:

1. If that date has a Daily Plan, use the current related version: ACTIVE when
   present, otherwise the latest version.
2. If there is no plan, use the dashboard Roadmap context commitment.
3. Otherwise use the profile default.
4. Use 60 only for missing legacy data.

When no `roadmapId` is supplied, retain the dashboard's deterministic latest
ACTIVE Roadmap as its default context. When multiple ACTIVE Roadmaps exist,
the frontend exposes a selector and sends the selected ID. A foreign Roadmap
ID returns `RESOURCE_NOT_FOUND`.

Actual study minutes continue to come only from effective progress history.

## API contract changes

### Profile setup and update

Keep the existing endpoints and field name:

```text
defaultDailyMinutes: integer, 15..480, multiple of 15
```

Update OpenAPI descriptions to explain that this is an account fallback, not
a mandatory quota.

### Roadmap summaries

Add owner-visible `dailyCommitmentMinutes` to `RoadmapSummaryResponse` and the
frontend `RoadmapSummary` type. This lets the Daily Plan modal preview the
Roadmap fallback without making the frontend authoritative.

### Daily Plan creation

Keep `availableMinutes` optional and align its validation/OpenAPI domain:

```text
availableMinutes: integer | null, 15..480, multiple of 15
```

The response continues to return the resolved snapshot in
`availableMinutes`.

### DRAFT version budget update

Add a small request/response operation for an existing DRAFT version:

```text
request:
  availableMinutes: integer, 15..480, multiple of 15
  entityVersion: non-negative integer

response:
  existing DailyPlanVersionResponse shape
```

### Dashboard

Accept optional `roadmapId`. Keep the existing response shape: every
`DailyStudyTimePointDto.targetMinutes` contains its resolved target. No account
or Roadmap fallback needs to be recomputed by the client.

## Database migration

Create the next migration after V22; do not edit V1, V3, or V22.

The migration should:

1. report/fail on existing nonconforming `user_profiles.default_daily_minutes`
   values rather than silently clamping them;
2. replace `ck_user_profiles_default_daily_minutes` with the 15-through-480,
   divisible-by-15 policy;
3. report/fail on existing nonconforming
   `daily_plan_versions.available_minutes` values;
4. replace `ck_daily_plan_versions_available_minutes` with the same range and
   increment policy;
5. preserve all existing Roadmaps, Daily Plans, versions, and progress data.

The existing Roadmap constraint from V22 remains unchanged. No column is
renamed and no historical budget is rewritten.

For disposable developer databases, the complete V1-through-new-head sequence
must still migrate cleanly. For retained databases, take a backup and run the
preflight queries before applying the constraint migration.

## Backend implementation slices

### PR 1 — Shared policy and profile alignment

Objective: make the account fallback use the approved domain.

Existing files likely changed:

- `controller/profile/dto/CompleteProfileSetupRequest.java`
- `controller/profile/dto/UpdateProfileRequest.java`
- `entity/profile/UserProfile.java`
- `service/profile/impl/ProfileServiceImpl.java`
- `controller/profile/ProfileController.java` only if OpenAPI operation text
  needs clarification
- profile controller/integration tests
- migration tests

New files likely required:

- `common/validation/StudyTimeBudgetPolicy.java`, or an equivalently neutral
  package not owned by Profile, Roadmap, or Daily Plan;
- the post-V22 Flyway migration.

Acceptance gate:

- 15, 270, and 480 succeed;
- 14, 37, and 481 return `VALIDATION_FAILED`;
- profile updates never modify Roadmap or DailyPlanVersion rows.

### PR 2 — Authoritative Daily Plan resolver

Objective: implement explicit -> Roadmap -> profile -> 60 exactly once.

Existing files likely changed:

- `controller/daily/dto/CreateDailyPlanRequest.java`
- `service/daily/impl/DailyPlanServiceImpl.java`
- `entity/daily/DailyPlanVersion.java`
- `controller/daily/dto/DailyPlanResponse.java`
- `controller/roadmap/dto/RoadmapSummaryResponse.java`
- `repository/profile/UserProfileRepository.java` only if a more suitable
  owner-scoped read is required
- Daily Plan service/controller tests

New files likely required:

- `service/daily/AvailableMinutesResolver.java`
- `service/daily/ResolvedAvailableMinutes.java`
- `service/daily/AvailableMinutesSource.java`
- focused resolver unit tests.

Important behavior:

- null `roadmapId` remains null;
- a foreign Roadmap cannot influence resolution;
- the resolved value is stored in version 1 before any AI work;
- response fallbacks use the shared system constant, not scattered literals.

### PR 3 — DRAFT budget editing and AI propagation

Objective: support a later explicit daily adjustment without rewriting history.

Existing files likely changed:

- `controller/daily/DailyPlanController.java`
- `service/daily/DailyPlanService.java`
- `service/daily/impl/DailyPlanServiceImpl.java`
- `entity/daily/DailyPlanVersion.java`
- `common/constant/ApiConstant.java`
- Daily Plan controller/service tests
- AI context and persistence tests where needed

New file likely required:

- `controller/daily/dto/UpdateDailyPlanBudgetRequest.java`.

Acceptance gate:

- only the owner can edit the budget;
- only DRAFT versions can be edited;
- stale entity versions fail with `CONCURRENT_MODIFICATION`;
- regeneration uses the persisted new budget;
- the previous version and its budget remain unchanged;
- AI prompt and validator receive the same value.

### PR 4 — Dashboard target resolution

Objective: make chart targets use actual plan-version snapshots.

Existing files likely changed:

- `controller/report/DashboardReportController.java`
- `service/report/DashboardReportService.java`
- `service/report/impl/DashboardReportServiceImpl.java`
- `repository/daily/DailyPlanRepository.java`
- `repository/daily/DailyPlanVersionRepository.java`
- dashboard service/controller tests

Implementation constraints:

- batch-load the seven-day plans and current versions; do not issue one query
  per chart day;
- use ACTIVE-or-latest version semantics already represented by
  `findCurrentVersionsByDailyPlanIds`;
- owner-scope an optional Roadmap before using its commitment;
- do not confuse planned target minutes with actual progress minutes.

## Frontend implementation slices

### PR 5 — Shared duration control and profile UX

Objective: provide one consistent 15-through-480-minute input.

Existing files likely changed:

- `features/roadmaps/roadmap-commitment.ts`
- `features/roadmaps/roadmap-commitment-field.tsx`
- `features/roadmaps/roadmap-onboarding-form.tsx`
- `features/profile/profile-setup-form.tsx`
- `app/(app)/profile/page.tsx`
- related tests and `types/api.ts`.

Preferred refactor:

- move generic calculation/formatting into `lib/study-duration.ts`;
- move the reusable control into
  `components/ui/study-duration-field.tsx`;
- leave a thin Roadmap wrapper only if Roadmap-specific explanatory copy is
  useful.

Profile copy must say the value is an account fallback. Quick choices remain
30 minutes, 1, 2, 4, 6, and 8 hours, with custom 15-minute increments.

### PR 6 — Daily Plan override UX

Objective: distinguish inherited budget from a deliberate override.

Existing files likely changed:

- `features/daily/daily-plans-view.tsx`
- `features/daily/daily-plan-detail-view.tsx`
- Daily Plan API helpers/types and new component tests.

Behavior:

1. With no daily override, show the proposed source and value:
   Roadmap commitment, profile fallback, or system fallback.
2. Submit `availableMinutes: null` when the user chooses inherited behavior.
3. Provide “Tùy chỉnh cho ngày này” using the shared duration control.
4. Selecting a Roadmap updates the preview unless the user has enabled an
   explicit override.
5. Daily Plan detail allows budget editing only for the selected DRAFT
   version.
6. Save a changed DRAFT budget before queueing AI regeneration.
7. Display the server-returned snapshot after creation; never assume the
   client preview is authoritative.

### PR 7 — Dashboard Roadmap context

Objective: display server-resolved targets consistently.

Existing files likely changed:

- `features/reports/report-api.ts`
- `app/(app)/dashboard/page.tsx`
- `features/reports/dashboard-stats-card.tsx`
- report API/component tests and `types/api.ts` if response documentation is
  clarified.

Behavior:

- load ACTIVE Roadmaps for the context selector;
- send optional `roadmapId` to the report endpoint;
- use each returned `targetMinutes` directly;
- remove the legend assumption that every day shares
  `dailyPoints[0].targetMinutes`;
- represent per-day targets correctly when snapshots differ.

## Test matrix

### Backend

- Profile setup/update boundaries and 15-minute increment.
- Database constraints for profile, Roadmap, and Daily Plan version budgets.
- Explicit daily value wins over Roadmap and profile.
- Roadmap wins over profile when explicit value is null.
- Profile wins when no Roadmap is selected.
- System 60 is used only by a focused legacy/missing-data resolver test.
- Null Roadmap no longer auto-links an ACTIVE Roadmap.
- Two users cannot resolve from or edit each other's Roadmap/plan/version.
- ADMIN receives 403 for profile, plan, and dashboard personal endpoints.
- Changing profile and Roadmap defaults leaves stored versions unchanged.
- DRAFT budget update enforces status and optimistic locking.
- AI context, prompt, persistence, and validation use one identical budget.
- Dashboard uses version snapshots for dates with plans and the correct
  fallback for dates without plans.
- Dashboard batch query count does not grow per displayed day.
- OpenAPI exposes minimum, maximum, and multiple-of constraints.

### Frontend

- Shared duration helper accepts 15, 270, 480 and rejects 14, 37, 481.
- Profile setup and editing save custom and boundary values.
- Roadmap onboarding remains green after extracting the shared component.
- Daily Plan modal sends null for inherited mode and a number for explicit
  override mode.
- Roadmap selection changes the inherited preview without overwriting a custom
  value.
- DRAFT detail saves a budget before regeneration.
- ACTIVE/SUPERSEDED versions do not show an editable budget control.
- Dashboard request includes the selected Roadmap ID.
- Chart renders different daily target markers without assuming one global
  target.
- Loading, API failure, validation, keyboard, and focus states remain usable.

## Acceptance-criteria traceability

| Acceptance criterion | Planned implementation |
|---|---|
| AC1 account default | Profile API/entity retains `defaultDailyMinutes`; PR 1 aligns semantics and validation |
| AC2 flexible value | Shared 15–480/15 policy, migration, shared frontend control |
| AC3 precedence | `AvailableMinutesResolver` in PR 2 |
| AC4 daily snapshot | Resolved value stored in `DailyPlanVersion`; later versions copy or explicitly edit their own DRAFT |
| AC5 no retroactive update | No cascade/update from Profile or Roadmap services; regression tests |
| AC6 dashboard consistency | Per-day plan-version target plus Roadmap/profile fallback in PR 4/7 |
| AC7 AI budget | Existing AI context/validator consume persisted snapshot; PR 3 adds explicit regression coverage |

## Safe implementation and merge order

```text
PR 1 shared policy/profile/migration
    -> PR 2 resolver and initial Daily Plan snapshot
        -> PR 3 DRAFT budget edit and AI propagation
            -> PR 4 dashboard backend
                -> PR 5 shared frontend control/profile
                    -> PR 6 Daily Plan UX
                        -> PR 7 dashboard UX
```

Backend PRs 1 through 4 should merge before frontend PRs 5 through 7. The
Roadmap summary contract from PR 2 is required by the Daily Plan frontend.

## Stop/go gates

1. **Migration gate:** retained data contains no value that would fail the new
   constraints, or the team explicitly approves remediation.
2. **Policy gate:** one test suite proves the same boundaries across Profile,
   Roadmap, Daily Plan request, and database checks.
3. **Resolution gate:** the four precedence cases pass without relying on the
   frontend.
4. **Snapshot gate:** changing Profile/Roadmap values cannot alter an existing
   DailyPlanVersion.
5. **AI gate:** prompt context and validator receive exactly the stored
   `availableMinutes`.
6. **Dashboard gate:** every day with a plan uses that plan's current version
   budget and queries remain batched.
7. **Frontend gate:** full tests, targeted lint, production build, and manual
   keyboard testing pass.

## Rollback points

- Before the constraint migration: database backup and invalid-row report.
- PR 1 may be reverted before applying the migration in shared environments.
- After migration, roll back with a forward migration that restores the wider
  constraints; do not edit or delete the applied migration.
- Resolver/UI changes are application-level and can be reverted without
  rewriting stored snapshots.
- Never roll back by changing existing DailyPlanVersion values.

## Definition of done

- All three time values retain distinct ownership and meaning.
- Profile and explicit daily values accept the same approved domain as
  Roadmap commitments.
- The server applies the exact precedence order and stores one snapshot.
- AI and server-side validation use that snapshot.
- Dashboard targets use plan snapshots when available.
- Existing Roadmaps and Daily Plan versions remain unchanged after default
  edits.
- No ADMIN access, cross-owner access, personal-value logging, or historical
  overwrite is introduced.
- Backend and frontend full suites pass from clean builds.

