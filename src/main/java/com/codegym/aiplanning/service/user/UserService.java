package com.codegym.aiplanning.service.user;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRoleRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    PageResponse<UserResponse> getUsers(UserSearchParam param);

    UserResponse getUserById(UUID id);

    UserResponse updateUserRole(UUID id, UpdateUserRoleRequest request, Jwt actorJwt);

    UserResponse activateUser(UUID id, Jwt actorJwt);

    UserResponse deactivateUser(UUID id, Jwt actorJwt);
}
