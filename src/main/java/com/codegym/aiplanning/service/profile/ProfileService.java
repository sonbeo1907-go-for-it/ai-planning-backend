package com.codegym.aiplanning.service.profile;

import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import com.codegym.aiplanning.controller.profile.dto.UpdateProfileRequest;
import java.util.UUID;

public interface ProfileService {

    ProfileResponse getCurrentProfile(UUID userId);

    ProfileResponse updateCurrentProfile(UUID userId, UpdateProfileRequest request);
}
