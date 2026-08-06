package com.codegym.aiplanning.controller.user;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRoleRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import com.codegym.aiplanning.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.USERS)
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
@Tag(name = "User Management", description = "Endpoints d\u00e0nh cho Admin qu\u1ea3n l\u00fd t\u00e0i kho\u1ea3n (US-ADM-01)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "L\u1ea5y danh s\u00e1ch ng\u01b0\u1eddi d\u00f9ng ph\u00e2n trang & t\u00ecm ki\u1ebfm", description = "H\u1ed7 tr\u1ee3 l\u1ecdc theo role, status v\u00e0 t\u00ecm ki\u1ebfm t\u1eeb kh\u00f3a username/email/fullName.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "L\u1ea5y danh s\u00e1ch th\u00e0nh c\u00f4ng"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Kh\u00f4ng c\u00f3 quy\u1ec1n Admin")
    })
    public ApiResponse<PageResponse<UserResponse>> getUsers(@ModelAttribute UserSearchParam param) {
        return ApiResponse.of(userService.getUsers(param));
    }

    @GetMapping("/{id}")
    @Operation(summary = "L\u1ea5y th\u00f4ng tin chi ti\u1ebft ng\u01b0\u1eddi d\u00f9ng theo ID", description = "Tr\u1ea3 v\u1ec1 th\u00f4ng tin chi ti\u1ebft c\u1ee7a m\u1ed9t t\u00e0i kho\u1ea3n theo UUID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "T\u00ecm th\u1ea5y t\u00e0i kho\u1ea3n"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng v\u1edbi ID t\u01b0\u01a1ng \u1ee9ng")
    })
    public ApiResponse<UserResponse> getUserById(
            @Parameter(description = "ID \u0111\u1ecbnh danh UUID c\u1ee7a ng\u01b0\u1eddi d\u00f9ng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id) {
        return ApiResponse.of(userService.getUserById(id));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "C\u1eadp nh\u1eadt vai tr\u00f2 c\u1ee7a ng\u01b0\u1eddi d\u00f9ng", description = "C\u1eadp nh\u1eadt vai tr\u00f2 ng\u01b0\u1eddi d\u00f9ng (kh\u00f4ng \u0111\u01b0\u1ee3c thay \u0111\u1ed5i vai tr\u00f2 Admin).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "C\u1eadp nh\u1eadt vai tr\u00f2 th\u00e0nh c\u00f4ng"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Y\u00eau c\u1ea7u kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c c\u1ed1 thay \u0111\u1ed5i vai tr\u00f2 Admin"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng")
    })
    public ApiResponse<UserResponse> updateUserRole(
            @Parameter(description = "ID \u0111\u1ecbnh danh UUID c\u1ee7a ng\u01b0\u1eddi d\u00f9ng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.updateUserRole(id, request, actorJwt));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "M\u1edf kh\u00f3a / K\u00edch ho\u1ea1t l\u1ea1i t\u00e0i kho\u1ea3n ng\u01b0\u1eddi d\u00f9ng", description = "Chuy\u1ec3n tr\u1ea1ng th\u00e1i t\u00e0i kho\u1ea3n th\u00e0nh ACTIVE v\u00e0 x\u00f3a c\u00e1c l\u1ea7n \u0111\u0103ng nh\u1eadp sai tr\u01b0\u1edbc \u0111\u00f3.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "M\u1edf kh\u00f3a / K\u00edch ho\u1ea1t t\u00e0i kho\u1ea3n th\u00e0nh c\u00f4ng"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng")
    })
    public ApiResponse<UserResponse> activateUser(
            @Parameter(description = "ID \u0111\u1ecbnh danh UUID c\u1ee7a ng\u01b0\u1eddi d\u00f9ng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.activateUser(id, actorJwt));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "V\u00f4 hi\u1ec7u h\u00f3a t\u00e0i kho\u1ea3n ng\u01b0\u1eddi d\u00f9ng (Soft disable)",
            description = "Chuy\u1ec3n t\u00e0i kho\u1ea3n kh\u00f4ng ph\u1ea3i Admin th\u00e0nh INACTIVE, thu h\u1ed3i to\u00e0n b\u1ed9 "
                    + "phi\u00ean \u0111\u0103ng nh\u1eadp v\u00e0 refresh token thay v\u00ec x\u00f3a c\u1ee9ng kh\u1ecfi CSDL.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "V\u00f4 hi\u1ec7u h\u00f3a t\u00e0i kho\u1ea3n th\u00e0nh c\u00f4ng"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Kh\u00f4ng th\u1ec3 v\u00f4 hi\u1ec7u h\u00f3a t\u00e0i kho\u1ea3n Admin"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng")
    })
    public ApiResponse<UserResponse> deactivateUser(
            @Parameter(description = "ID \u0111\u1ecbnh danh UUID c\u1ee7a ng\u01b0\u1eddi d\u00f9ng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.deactivateUser(id, actorJwt));
    }
}
