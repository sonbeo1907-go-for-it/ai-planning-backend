# Architecture baseline

The backend follows a layered, feature-oriented package structure. The retained
foundation contains authentication, profile, audit, email, API, persistence,
and security infrastructure. V2 business modules are added as independent
USER-owned aggregates.

```text
com.codegym.aiplanning
|-- controller/<feature>/dto/
|-- service/<feature>/impl/
|-- repository/<feature>/
|-- entity/<feature>/
|-- common/
`-- config/
```

## Layer responsibilities

| Layer | Responsibility |
|---|---|
| `controller` | HTTP endpoints, request validation, request/response DTOs |
| `service` | Interfaces, transactions, use-case orchestration and business rules |
| `service.<feature>.impl` | Implementations of service interfaces |
| `repository` | Persistence queries, including owner-scoped personal-resource queries |
| `entity` | JPA entities, aggregate state and business enums |
| `common` | Shared API models, constants, base entities and exceptions |
| `config` | Security, OpenAPI, CORS and application configuration |

## Dependency rules

1. Controllers depend on service interfaces, not implementation classes.
2. Services may use repositories and entities.
3. Repositories use entities and must not call controllers or services.
4. Entities do not depend on controllers, services or HTTP DTOs.
5. DTOs must not be reused as JPA entities.
6. Endpoint strings come from `ApiConstant`.
7. Business rules are enforced and tested in the backend.
8. Personal-resource queries include the authenticated owner in the database query.
9. Aggregate children are authorized through their aggregate root.
10. AI provider integrations remain behind the AI service boundary.

## V2 feature packages

The target feature packages are:

```text
profile
source
roadmap
progress
daily
review
ai
```

Instructor, StudyClass, Enrollment, WeeklyPlan, and institution-owned
course/curriculum packages are outside the V2 MVP.

## V2 invariants

- Every personal resource belongs to one authenticated USER.
- ADMIN has no implicit access to USER learning content.
- Roadmap and DailyPlan are aggregate roots with historical content versions.
- AI generation creates drafts and never overwrites USER-edited content.
- Progress is stored separately from planned content.
- AI Review is advisory and references one exact DailyPlanVersion.
- Uploaded document content is untrusted data, not AI instruction.
- Provider secrets and personal AI content do not enter logs or audit metadata.
- Manual workflows remain available when AI is unavailable.
- The active migration history starts from a clean V2 foundation baseline.
  Pre-V2 migrations remain available through Git history and must never be
  applied together with the clean baseline.

Ownership, state, and version invariants are protected at both the service and
database levels where PostgreSQL can enforce them.
