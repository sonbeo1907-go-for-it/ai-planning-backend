package com.codegym.aiplanning.controller.profile;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.PROFILE)
@Tag(name = "Profile", description = "Endpoints lấy thông tin tài khoản đang đăng nhập")
public class ProfileController {

    @GetMapping
    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "Lấy thông tin profile người dùng dựa trên Bearer Token truyền vào")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin profile thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực hoặc Token không hợp lệ")
    })
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        ProfileResponse response = new ProfileResponse(
                UUID.fromString(jwt.getClaimAsString("uid")),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("full_name"),
                jwt.getClaimAsStringList("roles"));
        return ApiResponse.of(response);
    }
}

