package com.codegym.aiplanning.service.profile;

import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import java.util.UUID;

public interface ProfileService {

    ProfileResponse getCurrentProfile(UUID userId);
}
