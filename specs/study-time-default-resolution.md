# Study-time default and resolution specification

## Status

Planned. This specification defines the distinct meanings and precedence of
account, Roadmap, and Daily Plan time values.

## User story

As a `USER`, I want an account-level default while retaining Roadmap-specific
commitments and per-day overrides, so that the system chooses a sensible study
budget without forcing me to re-enter the same value.

## Current gap

Three time values exist but are not consistently connected:

- `UserProfile.defaultDailyMinutes` is collected as a general preference;
- `Roadmap.dailyCommitmentMinutes` is collected during Roadmap onboarding;
- `DailyPlanVersion.availableMinutes` represents one plan version's budget.

Daily Plan creation currently falls back directly to 60 minutes when the
request omits `availableMinutes`. It therefore ignores both the selected
Roadmap commitment and the profile default. The dashboard uses the profile
default as a target even when a concrete Daily Plan budget is available. The
profile API and frontend also expose different effective ranges.

## Semantic model

| Field | Scope | Meaning | Mutation effect |
|---|---|---|---|
| `UserProfile.defaultDailyMinutes` | Account | Fallback preference when no more specific value exists | Does not rewrite Roadmaps or plans |
| `Roadmap.dailyCommitmentMinutes` | Roadmap | Normal daily commitment for this learning goal | Does not rewrite existing plan versions |
| `DailyPlanVersion.availableMinutes` | Plan version | Snapshot of the budget used for one target day/version | Immutable with that version's planned content |

None of these values represents actual time studied. Actual time continues to
come from progress records.

## Resolution precedence

When creating a Daily Plan version, resolve its budget exactly once in this
order:

```text
explicit request availableMinutes
    -> selected Roadmap dailyCommitmentMinutes
        -> UserProfile defaultDailyMinutes
            -> legacy/system fallback of 60 minutes
```

Rules:

1. Resolve the owner and selected Roadmap before resolving the budget.
2. An explicit per-day value always wins when valid.
3. A selected Roadmap's commitment wins over the account default.
4. The profile default is used when no selected Roadmap commitment exists.
5. The 60-minute system fallback exists only for missing legacy data; it must
   not bypass valid profile or Roadmap values.
6. Persist the result in `DailyPlanVersion.availableMinutes`.
7. Regeneration may accept a new explicit budget and creates a new version. It
   never rewrites an earlier version.
8. Manual and AI plan creation use the same resolver.

## Profile behavior

- Profile setup and profile editing support quick choices plus a custom value.
- `defaultDailyMinutes` accepts 15–480 minutes in 15-minute increments.
- The field is explained as a fallback, not a promise that every Roadmap or day
  will use that value.
- Changing the profile default affects only future resolutions where no more
  specific value is supplied.
- Existing Roadmaps and DailyPlanVersions are never updated retroactively.

## Roadmap behavior

- A Roadmap commitment remains independent from the profile default.
- Changing the profile default does not change Roadmap commitments.
- Changing a draft Roadmap commitment does not alter existing Daily Plan
  versions.
- An ACTIVE Roadmap remains subject to its existing metadata/version editing
  rules; this specification does not create an editing bypass.

## Daily Plan and AI behavior

- `CreateDailyPlanRequest.availableMinutes` remains optional.
- A null value invokes server-side resolution; it does not mean 60 minutes.
- The resolved value is returned in Daily Plan and DailyPlanVersion responses.
- AI prompt construction and server-side output validation both consume the
  persisted/resolved `availableMinutes` value.
- AI cannot exceed the budget and cannot silently modify the profile or
  Roadmap defaults.
- AI failure still permits manual plan creation using the same resolved budget.

## Dashboard behavior

- When the displayed day has a Daily Plan, the study target comes from the
  relevant DailyPlanVersion's `availableMinutes`.
- When the displayed day has no Daily Plan but a Roadmap is explicitly in
  context, use that Roadmap's commitment.
- Otherwise use `UserProfile.defaultDailyMinutes`.
- Use the 60-minute fallback only when retained legacy data lacks all three
  values.
- Actual accumulated minutes continue to derive from effective progress
  history and must not be confused with the target.

## API and validation

No new endpoint group is required. Affected contracts include:

- profile setup and profile update;
- Roadmap onboarding and Roadmap reads;
- Daily Plan creation and regeneration;
- dashboard/report responses where a target is displayed.

All user-entered values use the same validation policy: 15–480 integer minutes
in 15-minute increments. Invalid explicit values return `VALIDATION_FAILED`.

## Persistence and migration

- Keep all three existing integer columns; do not merge their meanings.
- Align database checks for profile and Roadmap defaults with the approved
  range and increment.
- Existing DailyPlanVersion values remain historical snapshots and are not
  migrated merely because defaults change.
- Before tightening a retained database constraint, report out-of-range rows
  and require an explicit remediation decision. Never silently clamp user data.
- Disposable development databases may adopt the corrected clean baseline only
  when the team confirms that no shared applied history must be preserved.

## Authorization and privacy

- All profile, Roadmap, Daily Plan, and dashboard lookups are owner-scoped.
- `ADMIN` does not gain access to personal learning-time values.
- Personal scheduling values and prompt context do not enter application logs
  or audit metadata.

## Required tests

- Explicit Daily Plan time overrides every fallback.
- Roadmap commitment is used when explicit time is absent.
- Profile default is used when both explicit time and Roadmap commitment are
  absent.
- System fallback is used only when all values are unavailable.
- A profile change does not modify existing Roadmaps or plan versions.
- A Roadmap change does not modify existing plan versions.
- Manual and AI generation resolve the same value.
- AI validation uses the resolved/persisted value.
- Dashboard uses the concrete plan budget when a plan exists.
- Profile and Roadmap fallback behavior is correct when no plan exists.
- Valid custom values and boundary values pass across frontend, API, and DB.
- Invalid range/increment values fail consistently.
- Owner isolation and ADMIN denial are covered at controller/integration level.

## Out of scope

- Weekday-specific recurring schedules.
- Calendar availability integration.
- Automatic adjustment from streak or productivity analysis.
- Rewriting historical Daily Plan budgets.
- Treating planned minutes as actual study time.

