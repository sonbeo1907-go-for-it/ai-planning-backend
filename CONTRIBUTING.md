# Contributing

## Branches

- `main`: always buildable.
- `feature/<jira-key>-short-name`: new work.
- `fix/<jira-key>-short-name`: bug fixes.
- `chore/<jira-key>-short-name`: tooling and maintenance.

Do not commit directly to `main`. Open a pull request and require at least one review.

## Commit messages

Use a short imperative subject prefixed by the Jira key:

```text
APL-123 add weekly plan overlap validation
```

## Before opening a pull request

```bash
./mvnw clean verify
```

The pull request must:

- implement one coherent Jira story or task;
- include tests for business rules;
- document API contract changes;
- add a Flyway migration for every schema change;
- never modify a Flyway migration that has already reached `main`;
- contain no password, access token, production URL, or `.env` file.

## Package conventions

Business code is grouped by layer, then by feature:

```text
controller/<feature>/       REST controllers
controller/<feature>/dto/   Request and response DTOs
service/<feature>/          Service interfaces
service/<feature>/impl/     Service implementations
repository/<feature>/       Spring Data repositories
entity/<feature>/           JPA entities and business enums
```

Controllers depend on service interfaces. Services coordinate entities and
repositories. Entities must not depend on controllers or HTTP-specific types.
API paths must use constants from `ApiConstant`.

Feature behavior and API contracts belong in the root `specs/` folder.
