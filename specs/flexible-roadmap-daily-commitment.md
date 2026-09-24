# Flexible Roadmap daily commitment specification

## Status

Implemented on `feature/US-RMP-ONB-05`. This specification replaces the fixed
30/60/120-minute onboarding choice with a validated Roadmap-scoped duration.

## User story

As a `USER`, I want to select or enter a realistic amount of study time per
day, including four, six, or eight hours, so that Roadmap generation reflects
my actual commitment.

## Current gap

The frontend, onboarding service, OpenAPI description, and database constraint
currently accept only `30`, `60`, or `120` minutes. The restriction rejects
valid commitments such as `240`, `360`, and `480` minutes even though the value
is already stored as an integer number of minutes.

## Scope and ownership

- `Roadmap.dailyCommitmentMinutes` remains Roadmap-scoped.
- It is not copied into or dynamically inherited from
  `UserProfile.defaultDailyMinutes`.
- Different Roadmaps owned by the same USER may have different commitments.
- A commitment is the normal budget for the Roadmap, not an immutable budget
  for every day.

## UI requirements

The onboarding time step provides:

- quick choices for 30 minutes, 1 hour, 2 hours, 4 hours, 6 hours, and 8 hours;
- a “Tùy chỉnh” option;
- a numeric input that may be presented in hours/minutes but submits minutes;
- a normalized preview, for example `270 phút (4 giờ 30 phút)`;
- inline validation without discarding previously entered wizard data.

The frontend must not maintain a smaller accepted range than the API.

## API contract

The existing field remains:

```text
dailyCommitmentMinutes: integer
```

No new endpoint is required. The field continues through:

- `PATCH /api/v1/roadmap-onboarding/{roadmapId}`;
- onboarding read/resume responses;
- Roadmap detail responses and AI Roadmap generation context.

OpenAPI must describe the range and increment rather than advertise an enum of
three values.

## Validation rules

- Minimum: 15 minutes.
- Maximum: 480 minutes.
- Increment: 15 minutes.
- Values are stored and transported as integer minutes.
- Completion requires a valid value.
- Partial saves may omit the field but may not supply an invalid value.

Examples:

| Input | Stored value | Result |
|---|---:|---|
| 30 minutes | `30` | valid |
| 4 hours | `240` | valid |
| 6 hours | `360` | valid |
| 8 hours | `480` | valid |
| 4 hours 30 minutes | `270` | valid |
| 10 minutes | `10` | invalid |
| 37 minutes | `37` | invalid |
| 9 hours | `540` | invalid |

Invalid values return `VALIDATION_FAILED` with a field-oriented message. The
backend remains authoritative even if the frontend already validates.

## Persistence and migration

- Keep `roadmaps.daily_commitment_minutes` as an integer.
- Replace the fixed-choice database check with a range-and-increment check
  equivalent to `15 <= value <= 480` and divisible by 15.
- Do not edit an already-applied Flyway migration in a retained database.
  Introduce the change after the current migration head when existing database
  history must be preserved.
- Existing values `30`, `60`, and `120` remain valid and need no conversion.
- No Roadmap or Daily Plan history is rewritten.

## AI behavior

- AI Roadmap generation receives the exact commitment stored on the Roadmap.
- AI output cannot mutate that commitment.
- Large budgets do not require AI to fill every minute. They represent a
  maximum/planning target, not a command to create unnecessary content.
- Prompt and structured-output validation must continue to enforce the
  relevant duration constraints without logging personal source content.

## Authorization and logging

- Only the authenticated owner may read or change the onboarding commitment.
- `ADMIN` cannot read or modify personal Roadmap commitments.
- Audit records contain the Roadmap ID and action, not the selected duration or
  other onboarding answers.

## Required tests

- Accept every quick choice, including 240, 360, and 480 minutes.
- Accept a custom valid value such as 270 minutes.
- Reject values below 15, above 480, and values not divisible by 15.
- Preserve the value through save, exit, resume, and completion.
- Keep commitments independent across two Roadmaps.
- Verify backend validation independently of frontend validation.
- Verify the database constraint accepts valid values and rejects invalid ones.
- Verify AI Roadmap context receives the stored value.
- Verify foreign-owner and ADMIN requests are denied safely.

## Out of scope

- Different commitments for individual weekdays.
- Automatically changing a commitment from observed study behavior.
- Removing or changing the separate 30/60/90-day expected-duration choices.
- Treating a Roadmap commitment as actual time spent.
