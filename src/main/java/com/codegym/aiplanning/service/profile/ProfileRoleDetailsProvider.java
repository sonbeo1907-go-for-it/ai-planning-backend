package com.codegym.aiplanning.service.profile;

import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.service.profile.model.ProfileRoleDetails;

/**
 * Extension point for domain modules that enrich the current-user profile.
 *
 * <p>The enrollment module can provide student data and the teaching-assignment module can
 * provide instructor data without changing the profile endpoint or accepting a user ID from a
 * client request.
 */
public interface ProfileRoleDetailsProvider {

    UserRole supportedRole();

    ProfileRoleDetails getDetails(UserAccount account);
}
