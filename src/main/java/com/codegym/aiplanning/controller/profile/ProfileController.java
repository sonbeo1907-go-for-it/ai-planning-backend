package com.codegym.aiplanning.controller.profile;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.profile.dto.ChangePasswordRequest;
import com.codegym.aiplanning.controller.profile.dto.CompleteProfileSetupRequest;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import com.codegym.aiplanning.controller.profile.dto.UpdateProfileRequest;
import com.codegym.aiplanning.service.auth.PasswordService;
import com.codegym.aiplanning.service.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.PROFILE)
public class ProfileController {

    private final ProfileService profileService;
    private final PasswordService passwordService;

    public ProfileController(ProfileService profileService, PasswordService passwordService) {
        this.profileService = profileService;
        this.passwordService = passwordService;
    }

    @GetMapping
    @Operation(
            summary = "Read the authenticated user's profile",
            description = "Returns the current account only; this endpoint never accepts a target user ID.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200", description = "Current profile"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "Authentication is required or the session has expired.",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(profileService.getCurrentProfile(UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Update the authenticated user's personal learning profile",
            description = "Updates only the authenticated USER's shared profile after initial setup. "
                    + "No administrator endpoint can access or edit it.")
    public ApiResponse<ProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(profileService.updateCurrentProfile(
                UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping(ApiConstant.PROFILE_SETUP)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Complete first-access profile setup",
            description = "Stores the display name, browser-detected IANA time zone and BCP 47 "
                    + "system language for the authenticated USER. Repeating the same request is safe; "
                    + "the original completion timestamp is preserved.")
    public ApiResponse<ProfileResponse> completeInitialSetup(
            @Valid @RequestBody CompleteProfileSetupRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(profileService.completeInitialSetup(
                UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping(ApiConstant.PASSWORD)
    @Operation(
            summary = "Change the authenticated user's password",
            description = "Verifies the current password, changes it, and revokes other sessions.")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID currentSessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        passwordService.changePassword(userId, currentSessionId, request);
        return ApiResponse.of(null);
    }
}
