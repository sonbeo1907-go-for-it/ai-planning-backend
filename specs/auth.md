# Authentication specification

## Scope

The MVP uses internal accounts identified for login by email. A user can
self-register a local Student account, while an administrator can create and
manage other account types. Authentication uses short-lived access JWTs and
database-backed, rotated refresh tokens. SSO and self-service password recovery
remain outside the current baseline.

## API constants

| Constant | Value |
|---|---|
| `ApiConstant.API_V1` | `/api/v1` |
| `ApiConstant.AUTH` | `/api/v1/auth` |
| `ApiConstant.LOGIN` | `/login` |
| `ApiConstant.REGISTER` | `/register` |
| `ApiConstant.REFRESH` | `/refresh` |
| `ApiConstant.LOGOUT` | `/logout` |
| `ApiConstant.AUTH_LOGIN` | `/api/v1/auth/login` |
| `ApiConstant.AUTH_REGISTER` | `/api/v1/auth/register` |
| `ApiConstant.AUTH_REFRESH` | `/api/v1/auth/refresh` |
| `ApiConstant.AUTH_LOGOUT` | `/api/v1/auth/logout` |
| `ApiConstant.PROFILE` | `/api/v1/profile` |

## Local self-registration (AUTH-12)

### Endpoint

```http
POST /api/v1/auth/register
Content-Type: application/json
```

This is a public endpoint; it requires no access token.

### Request

```json
{
  "email": "student@example.com",
  "password": "Password@123",
  "fullName": "Nguyen Van A"
}
```

Validation:

- `email` is required, must be valid and is at most 254 characters.
- `fullName` is required and is at most 150 characters.
- `password` is required, is 8-100 characters, and contains at least one
  uppercase letter, lowercase letter and digit.

For a valid request, the backend normalizes the email, hashes the password
with BCrypt, generates the retained internal `username`, and creates an
`ACTIVE` account with the `STUDENT` role. Clients cannot supply a role, status
or username.

### Response and email-enumeration protection

Every valid request returns the same response, including when the email is
already registered:

```http
HTTP/1.1 202 Accepted
```

The response has no body and does not issue a login session. The client should
show a generic acknowledgement and direct the user to the normal login flow.
Returning the same status and empty body prevents the API from revealing
whether an account is associated with an email. Invalid request fields still
return the standard `400 VALIDATION_FAILED` envelope.

Passwords must never be written to logs.

## Login

### Endpoint

```http
POST /api/v1/auth/login
Content-Type: application/json
```

### Request

```json
{
  "email": "admin@aiplanning.local",
  "password": "Admin@123"
}
```

Validation:

- `email` is required, must be a valid email address and is at most 254 characters.
- `password` is required and at most 200 characters.
- Email lookup is case-insensitive. Email values are normalized to lowercase before storage and authentication.
- `username` remains a unique internal/display identifier but is no longer accepted by the login endpoint.

### Successful response

```json
{
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Login creates a 14-day `auth_session` and returns a 15-minute access JWT. It
also sets a 256-bit opaque refresh token in an `HttpOnly` cookie. Only the
SHA-256 hash of the refresh token is persisted.

The access JWT contains:

- user ID in `sub` and `uid`;
- unique access-token ID in `jti`;
- login-session ID in `sid`;
- token purpose `typ=access`;
- normalized login email in `email`;
- username in `preferred_username`;
- display name in `full_name`;
- authorities in `roles`;
- issuer, issued time and expiration time.

## Session expiry (AUTH-03)

Access-token lifetime is configured with `JWT_EXPIRATION`; the default is
`PT15M`. Each login response includes the effective lifetime in `expiresIn`
(seconds). The backend validates token expiry on every protected request.

When a previously valid access token has expired, protected APIs return:

```json
{
  "timestamp": "2026-07-28T07:00:00Z",
  "status": 401,
  "code": "SESSION_EXPIRED",
  "message": "Your session has expired. Please sign in again.",
  "path": "/api/v1/profile",
  "requestId": "<request-id>"
}
```

Missing, malformed, incorrectly signed or wrong-issuer tokens return `401`
with `AUTHENTICATION_REQUIRED`. Clients must treat `401` with
`SESSION_EXPIRED` as an authentication event: preserve any locally held draft
state, warn about unsaved form data when navigation can be intercepted, then
send the user to the login screen. The backend never deletes persisted data
when a token expires.

### Failure response

Invalid credentials, inactive accounts, locked accounts and unknown emails
must not expose different messages to the client.

```json
{
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password.",
  "path": "/api/v1/auth/login",
  "requestId": "<request-id>"
}
```

Passwords and access tokens must never be written to logs.

## Refresh rotation

### Endpoint

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<opaque-token>
```

Each successful refresh consumes the presented token, creates a new refresh
token, replaces the HttpOnly cookie and issues a new access JWT. The session
has an absolute 14-day expiry; rotation never extends it.

Rotation is serialized with a short Redis lock and a PostgreSQL pessimistic row
lock. If Redis is unavailable, processing continues using the PostgreSQL lock.
PostgreSQL is the source of truth.

Presenting a previously consumed, revoked or expired refresh token revokes the
entire associated session and all refresh tokens in that session. The response
uses `INVALID_SESSION` without exposing the detailed reason.

## Logout

### Endpoint

```http
POST /api/v1/auth/logout
Authorization: Bearer <access-token>
Cookie: refresh_token=<opaque-token>
```

Logout is idempotent and returns `204 No Content`. It:

- resolves the current session from either a valid access token or any known
  refresh token;
- marks the `auth_session` revoked in PostgreSQL;
- revokes every refresh token belonging to that session;
- adds the access `jti` and session `sid` to Redis with bounded TTLs;
- clears the refresh cookie.

A valid refresh cookie can therefore log out a session even when the access
JWT has expired. The logout endpoint handles its bearer credential manually so
an expired bearer does not prevent cookie-based revocation.

All protected API access validates `typ`, `sid` and `jti`, checks Redis for a
fast denial, and confirms the session is still active and unexpired in
PostgreSQL. Consequently, logout invalidation still works when Redis is down
and does not fail open.

### Failed login limiting

Failed password attempts are counted per existing active account. After
`LOGIN_MAX_ATTEMPTS` consecutive failures, login for that account is
temporarily blocked for `LOGIN_BLOCK_DURATION`.

Defaults:

- maximum consecutive failures: `5`;
- temporary block duration: `PT15M` (15 minutes).

A successful login resets the failure count. Once the temporary block expires,
the next failed attempt starts a new counting window. Attempts against unknown,
inactive or administratively locked accounts are not persisted. All failures,
including a temporary block, return the same generic authentication response
and do not reveal whether an account exists or why authentication was denied.

Temporary login blocking is separate from the persistent `LOCKED` account
status. It is stored in `failed_login_attempts` and `login_blocked_until`.

## Current profile

### Endpoint

```http
GET /api/v1/profile
Authorization: Bearer <access-token>
```

### Response

```json
{
  "data": {
    "id": "<uuid>",
    "username": "admin",
    "email": "admin@aiplanning.local",
    "fullName": "System Administrator",
    "role": "ADMIN",
    "status": "ACTIVE"
  }
}
```

`email` is the primary login identifier. `username` is retained temporarily as
the user code/display identifier issued by the center. The endpoint always derives
the account from the authenticated JWT subject; it accepts no user ID, so a
client cannot retrieve another user's profile by changing a URL or request.

For a student, the response additionally contains a `student` section. For an
instructor, it additionally contains an `instructor` section:

```json
{
  "data": {
    "role": "STUDENT",
    "student": {
      "currentEnrollments": [
        {
          "enrollmentId": "<uuid>",
          "classId": "<uuid>",
          "classCode": "SE-2026-01",
          "className": "Software Engineering 2026.01",
          "courseId": "<uuid>",
          "courseCode": "JAVA-CORE",
          "courseName": "Java Core"
        }
      ]
    }
  }
}
```

```json
{
  "data": {
    "role": "INSTRUCTOR",
    "instructor": {
      "assignedClasses": [
        {
          "classId": "<uuid>",
          "classCode": "SE-2026-01",
          "className": "Software Engineering 2026.01",
          "courseId": "<uuid>",
          "courseCode": "JAVA-CORE",
          "courseName": "Java Core"
        }
      ]
    }
  }
}
```

The current codebase has no enrollment or instructor-assignment persistence
module, so these role-specific collections are currently empty. Future modules
add a Spring `ProfileRoleDetailsProvider` for their role to populate them;
neither the endpoint path nor its self-service authorization model changes.

## Account statuses

| Status | Login |
|---|---|
| `ACTIVE` | Allowed |
| `INACTIVE` | Denied |
| `LOCKED` | Denied |

## Roles

- `ROLE_STUDENT`
- `ROLE_INSTRUCTOR`
- `ROLE_ADMIN`

Role and data-scope authorization must be enforced by the backend.

## Email migration (AUTH-06)

Flyway migration `V5__add_email_login_identifier.sql` adds a non-null, unique,
lowercase `email` column. Existing accounts are backfilled as
`<lowercase-username>@legacy.local`; these placeholder addresses must be
replaced with real addresses through account management. The local bootstrap
Admin is created with `BOOTSTRAP_ADMIN_EMAIL`, defaulting to
`admin@aiplanning.local`. On an existing local database, the local seeder updates
the bootstrap Admin's placeholder address to the configured email.

New accounts require both a unique email and the temporarily retained unique
username. User-management responses and the current-profile response expose both.

## Account deactivation protection (AUTH-07)

Admin accounts cannot be deactivated through either `DELETE /api/v1/users/{id}`
or a `PUT /api/v1/users/{id}` status transition. Attempts return `409` with
`ADMIN_ACCOUNT_PROTECTED`; the account and its sessions remain unchanged.

When a non-Admin account becomes `INACTIVE`, the backend locks the account row
and then, in the same PostgreSQL transaction:

- marks every unexpired `ACTIVE` `auth_session` as `REVOKED` with reason
  `ACCOUNT_DEACTIVATED`;
- revokes every refresh token belonging to those sessions;
- commits the account status and audit log atomically.

After commit, each revoked session `sid` is added to Redis until that session's
original expiry. A session marker invalidates every access JWT issued for that
session, so individual access-token `jti` values do not need to be enumerated.
Protected requests still verify PostgreSQL, which remains authoritative if Redis
is unavailable. Login re-checks account status while holding the account lock so
a concurrent login cannot create a surviving session after deactivation.

### Real-time account deactivation event

Authenticated browser clients connect to:

```text
ws://localhost:8080/ws/account-events
```

For TLS deployments, use `wss://`. Immediately after the connection opens, the
client sends its access token in the first WebSocket message (never in the URL):

```json
{
  "type": "AUTHENTICATE",
  "accessToken": "<access-token>"
}
```

The token is validated with the same JWT, session, and revocation rules as a
protected API request. A successful channel authentication returns an
`AUTHENTICATED` event. After a non-Admin account is deactivated and the database
transaction commits, every connected browser for that user receives:

```json
{
  "type": "ACCOUNT_DEACTIVATED",
  "code": "ADMIN_DEACTIVATED_ACCOUNT",
  "message": "Your account has been deactivated.",
  "timestamp": "2026-08-03T00:00:00Z"
}
```

The server then closes the connection with code `4001`. The frontend clears its
in-memory access token and profile, shows a deactivation notice, and replaces the
current route with `/login?reason=account-deactivated`. API `401` handling remains
the fail-safe when the real-time connection is unavailable.

## Required tests

- Active account can log in by email.
- A public registration request creates an `ACTIVE` `STUDENT` account with a
  hashed password, and that account can log in.
- Duplicate local-registration requests receive the same acknowledgement and
  do not modify the existing account.
- Registration enforces the password policy.
- Email login is case-insensitive and the legacy username login payload is rejected.
- Duplicate account emails are rejected.
- Admin accounts cannot be deactivated through DELETE or status update.
- Deactivating a non-Admin account invalidates all access and refresh sessions.
- Revoked session IDs are cached in Redis after the database transaction commits.
- A connected user receives `ACCOUNT_DEACTIVATED` after commit and the event
  connection is closed.
- Wrong password returns the generic authentication error.
- Unknown email returns the same generic error.
- Locked and inactive accounts cannot log in.
- An active account is temporarily blocked after the configured number of
  consecutive failures.
- A successful login resets the consecutive-failure count.
- Temporary blocking returns the same generic authentication error.
- Valid JWT can access the profile endpoint.
- Missing, expired, incorrectly signed or wrong-issuer JWT is rejected.
- Login creates a refresh cookie and access JWT containing `jti` and `sid`.
- Logout invalidates the old access token and clears the refresh cookie.
- A valid refresh cookie can log out when the bearer token is expired.
- Refresh rotates the cookie and issues a new access token.
- Reuse of an old refresh token revokes the entire session.
- Logout is idempotent.
- Protected API access remains fail-closed when Redis is unavailable.
- Expired JWT returns `401 SESSION_EXPIRED` using the standard error envelope.
