package com.codegym.aiplanning.controller.profile;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.PROFILE)
public class ProfileController {

    @GetMapping
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        ProfileResponse response = new ProfileResponse(
                UUID.fromString(jwt.getClaimAsString("uid")),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("full_name"),
                jwt.getClaimAsStringList("roles"));
        return ApiResponse.of(response);
    }
}
