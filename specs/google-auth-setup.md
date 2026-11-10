# Google login setup

The backend accepts a Google Identity Services ID token and exchanges it for
the application's existing access JWT and HttpOnly refresh cookie. This flow
requires a Google OAuth Web Client ID. It does not require a Google API key or
client secret.

## Google Cloud configuration

1. Create or select a Google Cloud project.
2. Configure the OAuth consent screen.
3. Create an OAuth 2.0 Client ID with application type **Web application**.
4. Add the frontend origin, for example `http://localhost:3000`, to Authorized
   JavaScript origins.
5. Add development users to the consent screen's test-user list while the app
   remains in testing mode.

Google documentation:

- <https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid>
- <https://developers.google.com/identity/gsi/web/guides/verify-google-id-token>

## Backend environment

Copy `.env.example` to `.env` and update these values, or configure them in the
process/IDE that starts Spring Boot:

```dotenv
GOOGLE_AUTH_ENABLED=true
GOOGLE_CLIENT_ID=123456789-example.apps.googleusercontent.com
```

The `local` Spring profile imports the root `.env` file when present. Other
profiles use operating-system or deployment-platform environment variables and
do not load the local file.

The backend uses Google's OpenID Connect signing-key endpoint configured in
`application.yml`. Production must have outbound HTTPS access to Google so
signing keys can be downloaded and cached.

## Frontend contract

Load Google Identity Services with the same Web Client ID. Its credential
callback receives a Google ID token in `response.credential`. Send that value:

```http
POST http://localhost:8080/api/v1/auth/google
Content-Type: application/json
```

```json
{
  "idToken": "<response.credential>"
}
```

The response is identical to local login and sets the same refresh cookie:

```json
{
  "data": {
    "accessToken": "<local-jwt>",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Use the returned local access token for backend APIs. Do not use or persist the
Google ID token as the application's session token.

## Local verification

1. Start PostgreSQL, Redis and the backend.
2. Open Swagger UI at `http://localhost:8080/swagger-ui.html`.
3. Obtain a real Google ID token through the frontend Google button.
4. Call `POST /api/v1/auth/google` with that token.
5. Confirm that the response sets `refresh_token`, `/api/v1/profile` reports
   role `USER`, a default `user_profiles` row, and a null
   `user_accounts.password_hash`.

Google ID tokens are short-lived. Copying an old token into Swagger may return
`401 INVALID_GOOGLE_CREDENTIAL`; obtain a fresh credential and try again.
