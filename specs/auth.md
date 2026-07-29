# Authentication specification

## Scope

The MVP uses internal accounts. An administrator creates accounts and resets
passwords. SSO, refresh tokens and self-service password recovery are outside
the current baseline.

## API constants

| Constant | Value |
|---|---|
| `ApiConstant.API_V1` | `/api/v1` |
| `ApiConstant.AUTH` | `/api/v1/auth` |
| `ApiConstant.LOGIN` | `/login` |
| `ApiConstant.AUTH_LOGIN` | `/api/v1/auth/login` |
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
    "expiresIn": 28800
  }
}
```

The JWT contains:

- user ID in `sub` and `uid`;
- username in `preferred_username`;
- display name in `full_name`;
- authorities in `roles`;
- issuer, issued time and expiration time.

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
