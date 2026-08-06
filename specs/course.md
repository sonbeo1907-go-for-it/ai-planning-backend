# Course management specification

## Scope

Course management supports US-CUR-01 through US-CUR-04. All endpoints require
an authenticated Admin. Courses are never hard-deleted by this API.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/courses` | Create an active course |
| `GET` | `/api/v1/courses` | Search, filter and paginate courses |
| `GET` | `/api/v1/courses/{courseId}` | Read one course |
| `PATCH` | `/api/v1/courses/{courseId}` | Update name and description |
| `POST` | `/api/v1/courses/{courseId}/deactivate` | Change status to `INACTIVE` |
| `POST` | `/api/v1/courses/{courseId}/activate` | Change status to `ACTIVE` |

Every controller mapping is composed from constants in `ApiConstant`.
Successful calls use the standard `{ "data": ... }` response envelope.

## Course rules

- `code` is required, unique, normalized to uppercase and immutable after
  creation. It may contain letters, digits and single underscores.
- `name` is required and limited to 150 characters.
- `description` is optional and limited to 4000 characters.
- New courses always start as `ACTIVE`; clients cannot choose the initial
  status.
- Activate and deactivate operations are idempotent. Repeating the current
  state returns the current course without another database update or audit
  event.
- `created_by` and `updated_by` contain the authenticated Admin UUID.
- The response exposes the optimistic-lock `version`.

An `INACTIVE` course remains readable, and existing enrollment history is not
changed. Future class and enrollment services must reject creation against an
inactive course. Course deactivation itself does not delete or modify existing
classes or enrollments.

## Errors

| HTTP | Code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_FAILED` | Invalid body, query value or pagination |
| `401` | `AUTHENTICATION_REQUIRED` | Missing or invalid authentication |
| `403` | `ACCESS_DENIED` | Authenticated user is not an Admin |
| `404` | `COURSE_NOT_FOUND` | Course ID does not exist |
| `409` | `COURSE_CODE_ALREADY_EXISTS` | Normalized course code is already used |
| `409` | `CONCURRENT_MODIFICATION` | A stale concurrent update was detected |

## Audit

State-changing operations write audit events with resource `COURSE`, target
course ID, actor ID/username and the current request ID:

- `COURSE_CREATED`
- `COURSE_UPDATED`
- `COURSE_DEACTIVATED`
- `COURSE_ACTIVATED`
