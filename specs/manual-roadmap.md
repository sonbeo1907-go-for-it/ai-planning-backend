# Manual Roadmap specification

## Scope

An authenticated `USER` may own multiple independent Roadmaps. `ADMIN` has no
access to personal Roadmap content. Manual authoring is available without AI.

Creating a manual Roadmap creates both the Roadmap aggregate root and
`RoadmapVersion 1` in `DRAFT`. A Roadmap already created by onboarding may use
the version-creation endpoint to initialize its first content draft instead of
creating a duplicate Roadmap.

## Content hierarchy

A `RoadmapVersion` owns ordered `RoadmapItem` content:

```text
RoadmapVersion
|-- Milestone (for example, Week 1)
|   |-- Topic
|   `-- Topic
`-- Milestone (for example, Week 2)
    `-- Topic
```

Milestones have a title, optional description, and order. Topics additionally
require a positive estimated duration in minutes. The frontend owns drag-and-
drop interaction; the backend accepts and normalizes `orderIndex` values.

## Version rules

- Only `DRAFT` content is editable or deletable.
- Activation requires at least one Milestone and at least one Topic in every
  Milestone.
- Activation changes the version to `ACTIVE`, records its activation time,
  freezes its content, and updates `Roadmap.activeVersionId`.
- Creating a new draft after activation clones the active version, producing
  the next version number with origin `USER_EDITED`.
- Activating that draft changes the previous active version to `SUPERSEDED`;
  historical versions are never overwritten.
- At most one draft version may exist for a Roadmap.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/roadmaps` | Create a manual Roadmap and first draft version |
| `GET` | `/api/v1/roadmaps` | List owner-scoped Roadmaps |
| `GET` | `/api/v1/roadmaps/{roadmapId}` | Read Roadmap and version history |
| `POST` | `/api/v1/roadmaps/{roadmapId}/versions` | Create or clone the next draft version |
| `GET` | `/api/v1/roadmaps/{roadmapId}/versions/{versionId}` | Read exact version content |
| `POST` | `.../{versionId}/milestones` | Add a Milestone to a draft |
| `POST` | `.../{versionId}/milestones/{milestoneId}/topics` | Add a Topic |
| `PATCH` | `.../{versionId}/items/{itemId}` | Edit and reorder a draft item |
| `DELETE` | `.../{versionId}/items/{itemId}` | Delete draft content |
| `POST` | `.../{versionId}/activate` | Activate and freeze the version |

Every resource lookup is scoped through the authenticated owner. Another
owner receives `RESOURCE_NOT_FOUND`, which does not disclose resource existence.

## Privacy and audit

Roadmap titles, descriptions, Milestone text, and Topic text are personal
learning content. Request and response DTO string representations redact that
content. Audit records contain actor email, action, resource type, resource ID,
and request ID only.

This feature does not change DailyPlanItem deletion or ProgressEntry behavior;
that integration remains part of `US-PLN-01-MANUAL`.
