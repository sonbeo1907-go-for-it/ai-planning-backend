# Roadmap onboarding specification

## Responsibility boundary

The frontend owns the three-step wizard presentation, Back/Next navigation,
suggested-goal display, and in-session form state. The backend owns durable
resume state, validation, ownership, and the transition to a Roadmap draft.

Roadmap onboarding is available only to an authenticated `USER` after general
profile setup. An `ADMIN` has no access to these personal resources.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/roadmap-onboarding` | Start or resume the unfinished onboarding |
| `GET` | `/api/v1/roadmap-onboarding/current` | Reload the unfinished onboarding |
| `GET` | `/api/v1/roadmap-onboarding/{roadmapId}` | Reload one owner-scoped onboarding |
| `PATCH` | `/api/v1/roadmap-onboarding/{roadmapId}` | Save any completed wizard fields |
| `POST` | `/api/v1/roadmap-onboarding/{roadmapId}/complete` | Validate all fields and create the Roadmap draft |

Every endpoint derives the owner from the JWT subject. Supplying another
user's Roadmap ID returns `RESOURCE_NOT_FOUND` and never exposes its existence.

## Fields and fixed choices

- `goal`: trimmed, nonblank when supplied, maximum 500 characters.
- `proficiencyLevel`: `BEGINNER`, `BASIC`, or `INTERMEDIATE`.
- `dailyCommitmentMinutes`: `30`, `60`, or `120`.
- `expectedDurationDays`: `30`, `60`, or `90`.

Partial saves may omit unfinished fields so Back/Next navigation and later
resume do not discard earlier answers. Completion requires all four fields.

At most one Roadmap may be in `ONBOARDING` for an owner. Start/resume is
idempotent only while that onboarding remains unfinished. After completion,
`current` returns `RESOURCE_NOT_FOUND`; the next start creates another,
independent Roadmap onboarding. This permits one user to own multiple Roadmaps
without allowing ambiguous concurrent wizard sessions.

The proficiency, daily commitment, and expected duration are stored on the
Roadmap. The goal remains part of the same Roadmap aggregate as a user-owned
`GOAL` LearningSource linked through `RoadmapSource`, as required by the source
model. None of these values is stored in or dynamically inherited from
`UserProfile`. Changing a later Roadmap never changes an earlier Roadmap.

The daily commitment is scoped to this Roadmap and does not silently overwrite
`UserProfile.defaultDailyMinutes`.

## Domain transition

Starting onboarding creates a user-owned `Roadmap` in `ONBOARDING` and a linked
user-owned `LearningSource` of type `GOAL` in `DRAFT`. Goal text is untrusted
data and must never be promoted into AI system or developer instructions.

Completion atomically marks the goal source `READY` and the Roadmap `DRAFT`.
It does not activate the Roadmap, create an empty RoadmapVersion, or claim that
AI generation succeeded. A later Roadmap-generation capability consumes this
draft and creates versioned content. Retrying completion returns the same draft
and preserves its original completion timestamp.

## Audit and logging

Audit records contain only actor email, event type, resource type, Roadmap ID,
and request ID. Goal text and the other personal survey values are not copied
into audit metadata or application logs.
