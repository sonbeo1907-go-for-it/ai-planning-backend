# Authentication and profile specification

## Scope

The V2 foundation supports email/password registration, Google sign-in,
short-lived access JWTs, rotating refresh tokens, session revocation, login
attempt limiting, password reset, self-service password changes, request IDs,
and the authenticated user's profile.

Only `USER` and `ADMIN` roles exist:

- `USER` owns personal learning resources and a `UserProfile`.
- `ADMIN` is reserved for AI provider/model administration and has no implicit
  access to another user's account, profile, or learning resources.

The retired `STUDENT` and `INSTRUCTOR` roles are not accepted by the V2 schema.

## API constants and authorization

| Method | Path | Authorization |
|---|---|---|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/google` | Public |
| POST | `/api/v1/auth/refresh` | Refresh cookie |
| POST | `/api/v1/auth/logout` | Access token and/or refresh cookie |
| POST | `/api/v1/auth/password-reset-request` | Public |
| POST | `/api/v1/auth/password-reset` | Public reset token |
| GET | `/api/v1/profile` | Current `USER` or `ADMIN` |
| PATCH | `/api/v1/profile` | Current `USER` only |
| PUT | `/api/v1/profile/password` | Current account |

Every future path under `/api/v1/admin/**` requires `ROLE_ADMIN`. This route
boundary is exclusively for AI provider/model administration. There are no V2
administrator user-management APIs.

## Registration and Google sign-in

A valid local registration creates an `ACTIVE` `USER`, a generated
`user_<uuid>` internal username, a BCrypt password hash, and a default
`UserProfile`. Clients cannot choose a role or account status. Registration
returns `202 Accepted` without revealing whether the email already exists.

The Google flow verifies an ID token. A first-time identity creates the same
`USER` and profile with a null `password_hash`. An existing local account with
the same email must be linked explicitly rather than silently merged.

Email is normalized to lowercase and is the login identifier. The generated
username remains an internal stable identifier.

## Sessions and credentials

Login creates a PostgreSQL session and a hashed rotating refresh token. The
access JWT contains account ID, session ID, email, username, and one authority:
`ROLE_USER` or `ROLE_ADMIN`. Refresh-token reuse revokes the entire session.
Redis accelerates revocation and refresh locking; PostgreSQL remains
authoritative when Redis is unavailable.

Refresh tokens are returned only as HttpOnly cookies. Raw refresh tokens and
password-reset tokens are never persisted or logged. Failed local logins use
generic responses and a temporary `login_blocked_until` threshold to prevent
account enumeration.

## Current profile

`GET /api/v1/profile` derives the account from the authenticated JWT subject and
never accepts a target account ID. A USER response includes:

```json
{
  "data": {
    "id": "<uuid>",
    "username": "user_<uuid>",
    "email": "user@example.com",
    "fullName": "Example User",
    "role": "USER",
    "status": "ACTIVE",
    "preferences": {
      "timeZone": "Asia/Ho_Chi_Minh",
      "locale": "vi-VN",
      "defaultDailyMinutes": 60,
      "learningPreferences": "Prefer concise examples"
    }
  }
}
```

`PATCH /api/v1/profile` updates only the authenticated USER. Supported fields
are `fullName`, IANA `timeZone`, BCP 47 `locale`, `defaultDailyMinutes` from 1 to
1440, and optional `learningPreferences`. ADMIN may read its own account profile
but cannot create personal learning preferences.

## Audit boundary

Authentication and profile operations write event type, actor identifier,
target type, target identifier, and request ID. The audit write API deliberately
does not accept free-form details or metadata. Passwords, tokens, provider
secrets, AI prompts, uploaded document contents, and learning preference text
must never enter application logs or audit records.

## Required regression tests

- Local and Google registration create an `ACTIVE` `USER` and `UserProfile`.
- Only `USER` and `ADMIN` satisfy the database role constraint.
- Email login is case-insensitive and cannot enumerate accounts.
- Refresh rotation and reuse detection revoke sessions correctly.
- Redis failure does not bypass PostgreSQL session validation.
- Profile reads and updates are derived from the authenticated subject.
- ADMIN cannot update a personal learning profile.
- Audit records contain identifiers and request correlation only.
