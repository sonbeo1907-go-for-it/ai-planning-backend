package com.codegym.aiplanning.controller.auth;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.config.AuthSessionProperties;
import com.codegym.aiplanning.controller.auth.dto.LoginRequest;
import com.codegym.aiplanning.controller.auth.dto.TokenResponse;
import com.codegym.aiplanning.service.auth.AuthService;
import com.codegym.aiplanning.service.auth.model.AuthResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.AUTH)
@Tag(name = "Authentication", description = "Login, refresh-token rotation and logout")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionProperties sessionProperties;

    public AuthController(AuthService authService, AuthSessionProperties sessionProperties) {
        this.authService = authService;
        this.sessionProperties = sessionProperties;
    }

    @PostMapping(ApiConstant.LOGIN)
    @SecurityRequirements
    @Operation(
            summary = "Log in",
            description = "Authenticates an active internal account, creates a 14-day session, "
                    + "returns a 15-minute access JWT and sets the rotated refresh token as an "
                    + "HttpOnly cookie.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Login succeeded",
                headers = @Header(
                        name = HttpHeaders.SET_COOKIE,
                        description = "HttpOnly refresh_token cookie",
                        schema = @Schema(type = "string"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation =
                        com.codegym.aiplanning.common.api.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Invalid credentials, inactive account, locked account or "
                        + "temporary login block",
                content = @Content(schema = @Schema(implementation =
                        com.codegym.aiplanning.common.api.ApiError.class)))
    })
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.username(), request.password());
        return tokenResponse(result);
    }

    @PostMapping(ApiConstant.REFRESH)
    @SecurityRequirement(name = "refreshCookie")
    @Operation(
            summary = "Refresh the login session",
            description = "Consumes the current refresh token, rotates the HttpOnly cookie and "
                    + "issues a new 15-minute access JWT. Reuse of an old refresh token revokes "
                    + "the entire login session.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Refresh token rotated",
                headers = @Header(
                        name = HttpHeaders.SET_COOKIE,
                        description = "New HttpOnly refresh_token cookie",
                        schema = @Schema(type = "string"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Refresh token or login session is invalid or expired",
                content = @Content(schema = @Schema(implementation =
                        com.codegym.aiplanning.common.api.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Another refresh request is already in progress",
                content = @Content(schema = @Schema(implementation =
                        com.codegym.aiplanning.common.api.ApiError.class)))
    })
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Parameter(
                            name = "refresh_token",
                            description = "Rotated opaque refresh token. Normally supplied "
                                    + "automatically by the browser as an HttpOnly cookie.",
                            in = ParameterIn.COOKIE,
                            required = true,
                            schema = @Schema(type = "string", format = "password"))
            @CookieValue(name = "${app.security.session.refresh-cookie-name}",
                    required = false)
                    String refreshToken) {
        return tokenResponse(authService.refresh(refreshToken));
    }

    @PostMapping(ApiConstant.LOGOUT)
    @SecurityRequirements({
        @SecurityRequirement(name = "bearerAuth"),
        @SecurityRequirement(name = "refreshCookie")
    })
    @Operation(
            summary = "Log out",
            description = "Idempotently revokes the current session and every refresh token in "
                    + "it, then clears the refresh cookie. Either credential may identify the "
                    + "session; a valid refresh cookie still works when the access JWT expired.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Session revoked, or no active session remained",
                headers = @Header(
                        name = HttpHeaders.SET_COOKIE,
                        description = "Expired refresh_token cookie",
                        schema = @Schema(type = "string")))
    })
    public ResponseEntity<Void> logout(
            @Parameter(
                            name = HttpHeaders.AUTHORIZATION,
                            description = "Bearer access JWT. Optional when a known refresh "
                                    + "cookie is present.",
                            in = ParameterIn.HEADER,
                            example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                    String authorizationHeader,
            @Parameter(
                            name = "refresh_token",
                            description = "Opaque refresh token. Optional when a valid Bearer "
                                    + "access JWT is present.",
                            in = ParameterIn.COOKIE,
                            schema = @Schema(type = "string", format = "password"))
            @CookieValue(name = "${app.security.session.refresh-cookie-name}",
                    required = false)
                    String refreshToken) {
        authService.logout(authorizationHeader, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthResult result) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken(), result.refreshExpiresIn()).toString())
                .body(ApiResponse.of(TokenResponse.from(result.accessToken())));
    }

    private ResponseCookie refreshCookie(String value, long expiresIn) {
        return ResponseCookie.from(sessionProperties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(sessionProperties.refreshCookieSecure())
                .sameSite(sessionProperties.refreshCookieSameSite())
                .path(sessionProperties.refreshCookiePath())
                .maxAge(Duration.ofSeconds(expiresIn))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(sessionProperties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(sessionProperties.refreshCookieSecure())
                .sameSite(sessionProperties.refreshCookieSameSite())
                .path(sessionProperties.refreshCookiePath())
                .maxAge(0)
                .build();
    }
}
