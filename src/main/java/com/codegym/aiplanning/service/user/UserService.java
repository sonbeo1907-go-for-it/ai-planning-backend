package com.codegym.aiplanning.service.user;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.user.dto.CreateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    UserResponse createUser(CreateUserRequest request, Jwt actorJwt);

    PageResponse<UserResponse> getUsers(UserSearchParam param);

    UserResponse getUserById(UUID id);

    UserResponse updateUser(UUID id, UpdateUserRequest request, Jwt actorJwt);

    UserResponse deactivateUser(UUID id, Jwt actorJwt);
}
