package com.codegym.aiplanning.controller.auth;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.auth.dto.LoginRequest;
import com.codegym.aiplanning.controller.auth.dto.TokenResponse;
import com.codegym.aiplanning.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.AUTH)
@Tag(name = "Authentication", description = "Endpoints đăng nhập và xác thực tài khoản")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(ApiConstant.LOGIN)
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực tài khoản bằng username và password, trả về Bearer JWT Access Token.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về Bearer Token"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai tên đăng nhập hoặc mật khẩu")
    })
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(
                TokenResponse.from(authService.login(request.username(), request.password())));
    }
}

