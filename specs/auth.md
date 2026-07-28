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
- Valid JWT can access the profile endpoint.
- Missing, expired, incorrectly signed or wrong-issuer JWT is rejected.
