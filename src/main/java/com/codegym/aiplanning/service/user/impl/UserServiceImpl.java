package com.codegym.aiplanning.service.user.impl;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRoleRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.user.UserService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserAccountRepository userRepository;
    private final AuditLogService auditLogService;

    public UserServiceImpl(
            UserAccountRepository userRepository,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(UserSearchParam param) {
        Pageable pageable = PageRequest.of(
                param.resolvedPage(), param.resolvedSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<UserAccount> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param.search() != null && !param.search().isBlank()) {
                String searchLike = "%" + param.search().trim().toLowerCase() + "%";
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), searchLike);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchLike);
                Predicate fullNameMatch = cb.like(cb.lower(root.get("fullName")), searchLike);
                predicates.add(cb.or(usernameMatch, emailMatch, fullNameMatch));
            }

            if (param.role() != null) {
                predicates.add(cb.equal(root.get("role"), param.role()));
            }

            if (param.status() != null) {
                predicates.add(cb.equal(root.get("status"), param.status()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UserResponse> page = userRepository.findAll(spec, pageable).map(UserResponse::from);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));
        return UserResponse.from(account);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(UUID id, UpdateUserRoleRequest request, Jwt actorJwt) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));

        if (account.getRole() == UserRole.ADMIN) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Không được phép thay đổi vai trò của tài khoản Admin");
        }

        account.updateProfile(account.getFullName(), request.role(), account.getStatus());
        UserAccount updated = userRepository.save(account);

        auditLogService.logAction(
                extractActorId(actorJwt),
                extractActorUsername(actorJwt),
                AuditEventAction.USER_UPDATED,
                "USER",
                updated.getId().toString(),
                String.format("Updated role for user '%s' to '%s'", updated.getUsername(), updated.getRole()));

        return UserResponse.from(updated);
    }

    @Override
    @Transactional
    public UserResponse activateUser(UUID id, Jwt actorJwt) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));

        account.setStatus(com.codegym.aiplanning.entity.auth.AccountStatus.ACTIVE);
        account.clearLoginFailures();
        UserAccount updated = userRepository.save(account);

        auditLogService.logAction(
                extractActorId(actorJwt),
                extractActorUsername(actorJwt),
                AuditEventAction.USER_STATUS_CHANGED,
                "USER",
                updated.getId().toString(),
                String.format("Activated/Unlocked user account '%s'", updated.getUsername()));

        return UserResponse.from(updated);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(UUID id, Jwt actorJwt) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));

        if (account.getRole() == UserRole.ADMIN) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Không thể vô hiệu hóa tài khoản Admin");
        }

        account.deactivate();
        UserAccount updated = userRepository.save(account);

        auditLogService.logAction(
                extractActorId(actorJwt),
                extractActorUsername(actorJwt),
                AuditEventAction.USER_DISABLED,
                "USER",
                updated.getId().toString(),
                String.format("Disabled user account '%s'", updated.getUsername()));

        return UserResponse.from(updated);
    }

    private UUID extractActorId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String uidStr = jwt.getClaimAsString("uid");
        return (uidStr != null && !uidStr.isBlank()) ? UUID.fromString(uidStr) : null;
    }

    private String extractActorUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return jwt.getSubject() != null ? jwt.getSubject() : "anonymous";
    }
}
