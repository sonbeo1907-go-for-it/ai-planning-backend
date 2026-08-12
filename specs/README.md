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
learning-source.md
roadmap.md
daily-plan.md
progress.md
ai-review.md
ai-provider.md
```

Instructor, StudyClass, Enrollment, WeeklyPlan, and institution-owned
course/curriculum modules are outside the V2 MVP. The project uses a clean V2
Flyway baseline because all pre-V2 databases were declared disposable; the old
migrations remain available through Git history only.

The specification must be updated in the same pull request when an API contract
or business rule changes.
