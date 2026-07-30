# Authentication specification

## Scope

The MVP uses internal accounts. An administrator creates accounts and resets
passwords. Authentication uses short-lived access JWTs and database-backed,
rotated refresh tokens. SSO and self-service password recovery remain outside
the current baseline.

## API constants

| Constant | Value |
|---|---|
| `ApiConstant.API_V1` | `/api/v1` |
| `ApiConstant.AUTH` | `/api/v1/auth` |
| `ApiConstant.LOGIN` | `/login` |
| `ApiConstant.REFRESH` | `/refresh` |
| `ApiConstant.LOGOUT` | `/logout` |
| `ApiConstant.AUTH_LOGIN` | `/api/v1/auth/login` |
| `ApiConstant.AUTH_REFRESH` | `/api/v1/auth/refresh` |
| `ApiConstant.AUTH_LOGOUT` | `/api/v1/auth/logout` |
| `ApiConstant.PROFILE` | `/api/v1/profile` |

## Login

### Endpoint

```http
POST /api/v1/auth/login
Content-Type: application/json
```

### Request

```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

Validation:

- `username` is required and at most 100 characters.
- `password` is required and at most 200 characters.
- The username lookup is case-insensitive.

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

Invalid credentials, inactive accounts, locked accounts and unknown usernames
must not expose different messages to the client.

```json
{
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid username or password.",
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
    "fullName": "System Administrator",
    "roles": ["ROLE_ADMIN"]
  }
}
```

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

## Required tests

- Active account can log in.
- Wrong password returns the generic authentication error.
- Unknown username returns the same generic error.
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
