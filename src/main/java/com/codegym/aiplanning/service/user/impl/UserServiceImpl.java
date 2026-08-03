package com.codegym.aiplanning.service.user.impl;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.user.dto.CreateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.user.UserService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserServiceImpl(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, Jwt actorJwt) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Username already exists: " + username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Email already exists: " + email);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        UserAccount account = UserAccount.create(
                username,
                email,
                passwordHash,
                request.fullName().trim(),
                request.role(),
                request.resolvedStatus());

        UserAccount saved = userRepository.save(account);

        auditLogService.logAction(
                extractActorId(actorJwt),
                extractActorUsername(actorJwt),
                AuditEventAction.USER_CREATED,
                "USER",
                saved.getId().toString(),
                String.format("Created user '%s' with role '%s'", saved.getUsername(), saved.getRole()));

        return UserResponse.from(saved);
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
    public UserResponse updateUser(UUID id, UpdateUserRequest request, Jwt actorJwt) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));

        AccountStatus oldStatus = account.getStatus();
        if (request.email() != null && !request.email().isBlank()) {
            String normalizedEmail = normalizeEmail(request.email());
            if (!account.getEmail().equals(normalizedEmail)
                    && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "Email already exists: " + normalizedEmail);
            }
            account.changeEmail(normalizedEmail);
        }
        account.updateProfile(request.fullName(), request.role(), request.status());

        if (request.password() != null && !request.password().isBlank()) {
            account.changePassword(passwordEncoder.encode(request.password()));
        }

        UserAccount updated = userRepository.save(account);

        AuditEventAction action = (oldStatus != updated.getStatus())
                ? AuditEventAction.USER_STATUS_CHANGED
                : AuditEventAction.USER_UPDATED;

        auditLogService.logAction(
                extractActorId(actorJwt),
                extractActorUsername(actorJwt),
                action,
                "USER",
                updated.getId().toString(),
                String.format("Updated user '%s', role='%s', status='%s'", updated.getUsername(), updated.getRole(), updated.getStatus()));

        return UserResponse.from(updated);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(UUID id, Jwt actorJwt) {
        UserAccount account = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found with id: " + id));

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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
