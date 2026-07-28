# Architecture baseline

The backend follows a layered package structure. Each layer contains a
sub-package for the corresponding requirement or entity group.

```text
com.codegym.aiplanning
├── controller
│   ├── auth
│   │   └── dto
│   └── profile
│       └── dto
├── service
│   └── auth
│       └── impl
├── repository
│   └── auth
├── entity
│   ├── auth
│   └── plan
├── common
│   ├── api
│   ├── constant
│   ├── entity
│   └── exception
└── config
```

## Layer responsibilities

| Layer | Responsibility |
|---|---|
| `controller` | HTTP endpoints, request validation, request/response DTOs |
| `service` | Interfaces, transactions, use-case orchestration and business rules |
| `service.<feature>.impl` | Implementations of service interfaces |
| `repository` | Spring Data repositories and persistence queries |
| `entity` | JPA entities and business enums |
| `common` | Shared API models, constants, base entities and exceptions |
| `config` | Security, OpenAPI, CORS and application configuration |

## Dependency rules

1. Controllers depend on service interfaces, not implementation classes.
2. Services may use repositories and entities.
3. Repositories use entities and must not call controllers or services.
4. Entities do not depend on controllers, services or HTTP DTOs.
5. DTOs must not be reused as JPA entities.
6. Endpoint strings must come from `ApiConstant`.
7. Business rules must be enforced and tested in the backend.

## Feature package convention

When implementing DailyPlan:

```text
controller/daily/
controller/daily/dto/
service/daily/DailyPlanService.java
service/daily/impl/DailyPlanServiceImpl.java
repository/daily/DailyPlanRepository.java
entity/daily/DailyPlan.java
```

WeeklyPlan, curriculum, enrollment, progress and review follow the same
structure.

## Planning invariants

- `PlanReviewStatus` and `PlanExecutionStatus` are independent.
- Submitted revisions are immutable.
- Progress is stored separately and never mutates a submitted revision.
- A current DailyPlan is unique for an enrollment and plan date.
- Current WeeklyPlan ranges must not overlap within an enrollment.
- A DailyPlan may belong to a WeeklyPlan or be standalone.

These invariants must be protected at both service and database levels where
PostgreSQL supports the required constraint.
