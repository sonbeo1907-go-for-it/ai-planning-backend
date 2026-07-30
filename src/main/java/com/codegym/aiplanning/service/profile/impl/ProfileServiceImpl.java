package com.codegym.aiplanning.service.profile.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.profile.ProfileRoleDetailsProvider;
import com.codegym.aiplanning.service.profile.ProfileService;
import com.codegym.aiplanning.service.profile.model.ProfileRoleDetails;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final Map<UserRole, ProfileRoleDetailsProvider> detailsProviders;

    public ProfileServiceImpl(
            UserAccountRepository userAccountRepository,
            List<ProfileRoleDetailsProvider> detailsProviders) {
        this.userAccountRepository = userAccountRepository;
        this.detailsProviders = new EnumMap<>(UserRole.class);
        for (ProfileRoleDetailsProvider detailsProvider : detailsProviders) {
            ProfileRoleDetailsProvider previous =
                    this.detailsProviders.put(detailsProvider.supportedRole(), detailsProvider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Only one profile details provider may support role "
                                + detailsProvider.supportedRole());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(UUID userId) {
        UserAccount account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));

        ProfileRoleDetails details = detailsProviders
                .containsKey(account.getRole())
                ? detailsProviders.get(account.getRole()).getDetails(account)
                : ProfileRoleDetails.emptyFor(account.getRole());

        return ProfileResponse.from(account, details);
    }
}
