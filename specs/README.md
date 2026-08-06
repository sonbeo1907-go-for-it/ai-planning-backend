# Backend specifications

This folder contains implementation-facing specifications derived from the
approved SRS and Jira backlog.

Each feature specification should describe:

1. Scope and actors.
2. API endpoints.
3. Request and response models.
4. Authorization rules.
5. Business rules and state transitions.
6. Validation and error codes.
7. Persistence considerations.
8. Required tests.

Recommended files:

```text
auth.md
course.md
organization.md
curriculum.md
weekly-plan.md
daily-plan.md
progress.md
review.md
```

The specification must be updated in the same pull request when an API contract
or business rule changes.
