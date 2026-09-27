package com.codegym.aiplanning.service.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AvailableMinutesResolverTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Test
    void explicitDayOverrideWins() {
        UUID userId = UUID.randomUUID();
        AvailableMinutesResolver resolver = new AvailableMinutesResolver(userProfileRepository);

        ResolvedAvailableMinutes resolved = resolver.resolve(userId, 270, null);

        assertThat(resolved.minutes()).isEqualTo(270);
        assertThat(resolved.source()).isEqualTo(AvailableMinutesSource.EXPLICIT_DAY);
    }

    @Test
    void roadmapCommitmentWinsOverProfile() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId);
        Roadmap roadmap = Roadmap.manualDraft(user, "Java", null);
        ReflectionTestUtils.setField(roadmap, "dailyCommitmentMinutes", 240);
        AvailableMinutesResolver resolver = new AvailableMinutesResolver(userProfileRepository);

        ResolvedAvailableMinutes resolved = resolver.resolve(userId, null, roadmap);

        assertThat(resolved.minutes()).isEqualTo(240);
        assertThat(resolved.source()).isEqualTo(AvailableMinutesSource.ROADMAP);
    }

    @Test
    void profileWinsWhenNoRoadmapCommitmentExists() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(user(userId), "Learner");
        profile.update(null, null, null, 90);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        AvailableMinutesResolver resolver = new AvailableMinutesResolver(userProfileRepository);

        ResolvedAvailableMinutes resolved = resolver.resolve(userId, null, null);

        assertThat(resolved.minutes()).isEqualTo(90);
        assertThat(resolved.source()).isEqualTo(AvailableMinutesSource.PROFILE);
    }

    @Test
    void systemFallbackIsUsedOnlyWhenProfileIsMissing() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        AvailableMinutesResolver resolver = new AvailableMinutesResolver(userProfileRepository);

        ResolvedAvailableMinutes resolved = resolver.resolve(userId, null, null);

        assertThat(resolved.minutes()).isEqualTo(60);
        assertThat(resolved.source()).isEqualTo(AvailableMinutesSource.SYSTEM_FALLBACK);
    }

    private UserAccount user(UUID userId) {
        UserAccount user = UserAccount.create(
                "learner@example.com", "Password@123", UserRole.USER, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
