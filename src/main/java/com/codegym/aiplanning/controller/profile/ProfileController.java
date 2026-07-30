package com.codegym.aiplanning.controller.profile;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import com.codegym.aiplanning.service.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.PROFILE)
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(
            summary = "Read the authenticated user's profile",
            description = "Returns the current user only; this endpoint never accepts a target user ID.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200", description = "Current profile"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "Authentication is required.",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(profileService.getCurrentProfile(UUID.fromString(jwt.getSubject())));
    }
}
