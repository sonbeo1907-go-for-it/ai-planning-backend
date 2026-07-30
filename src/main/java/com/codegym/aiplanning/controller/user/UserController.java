package com.codegym.aiplanning.controller.user;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.user.dto.CreateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import com.codegym.aiplanning.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.USERS)
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
@Tag(name = "User Management", description = "Endpoints dành cho Admin quản lý tài khoản (US-ADM-01)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản người dùng mới (Học viên, Giảng viên, Admin)", description = "Yêu cầu quyền ADMIN. Mã đăng nhập phải duy nhất.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo tài khoản thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền Admin"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mã đăng nhập đã tồn tại")
    })
    public ApiResponse<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.createUser(request, actorJwt));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách người dùng phân trang & tìm kiếm", description = "Hỗ trợ lọc theo role, status và tìm kiếm từ khóa username/fullName.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    })
    public ApiResponse<PageResponse<UserResponse>> getUsers(@ModelAttribute UserSearchParam param) {
        return ApiResponse.of(userService.getUsers(param));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết người dùng theo ID", description = "Trả về thông tin chi tiết của một tài khoản theo UUID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm thấy tài khoản"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng với ID tương ứng")
    })
    public ApiResponse<UserResponse> getUserById(
            @Parameter(description = "ID định danh UUID của người dùng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id) {
        return ApiResponse.of(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin người dùng", description = "Cập nhật họ tên, vai trò, trạng thái hoặc đổi mật khẩu.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu cập nhật không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    public ApiResponse<UserResponse> updateUser(
            @Parameter(description = "ID định danh UUID của người dùng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.updateUser(id, request, actorJwt));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Vô hiệu hóa tài khoản người dùng (Soft disable)", description = "Chuyển trạng thái tài khoản thành INACTIVE thay vì xóa cứng khỏi CSDL.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vô hiệu hóa tài khoản thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    public ApiResponse<UserResponse> deactivateUser(
            @Parameter(description = "ID định danh UUID của người dùng", example = "a8c6cae3-cd19-425c-a4c1-a2ed63290854")
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actorJwt) {
        return ApiResponse.of(userService.deactivateUser(id, actorJwt));
    }
}

