package com.codegym.aiplanning.service.onboarding.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.onboarding.dto.RoadmapOnboardingResponse;
import com.codegym.aiplanning.controller.onboarding.dto.SaveRoadmapOnboardingRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.source.LearningSource;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.source.LearningSourceRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.onboarding.RoadmapOnboardingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapOnboardingServiceImpl implements RoadmapOnboardingService {

    private static final Set<Integer> DAILY_COMMITMENT_OPTIONS = Set.of(30, 60, 120);
    private static final Set<Integer> EXPECTED_DURATION_OPTIONS = Set.of(30, 60, 90);

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final LearningSourceRepository learningSourceRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;
    private final AuditLogService auditLogService;

    public RoadmapOnboardingServiceImpl(
            UserAccountRepository userAccountRepository,
            UserProfileRepository userProfileRepository,
            LearningSourceRepository learningSourceRepository,
            RoadmapRepository roadmapRepository,
            RoadmapSourceRepository roadmapSourceRepository,
            AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.learningSourceRepository = learningSourceRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public RoadmapOnboardingResponse startOrResume(UUID userId) {
        UserAccount user = requireEligibleUserForUpdate(userId);
        Roadmap existing = roadmapRepository
                .findByOwnerIdAndStatus(userId, RoadmapStatus.ONBOARDING)
                .orElse(null);
        if (existing != null) {
            return response(existing);
        }

        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.beginOnboarding(user));
        LearningSource goalSource = learningSourceRepository.saveAndFlush(
                LearningSource.goalDraft(user));
        roadmapSourceRepository.saveAndFlush(RoadmapSource.link(roadmap, goalSource));
        auditLogService.logAction(
                userId,
                user.getEmail(),
                AuditEventAction.ROADMAP_ONBOARDING_STARTED,
                "Roadmap",
                roadmap.getId().toString());
        return RoadmapOnboardingResponse.from(roadmap, goalSource);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapOnboardingResponse getCurrent(UUID userId) {
        Roadmap roadmap = roadmapRepository
                .findByOwnerIdAndStatus(userId, RoadmapStatus.ONBOARDING)
                .orElseThrow(this::onboardingNotFound);
        return response(roadmap);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapOnboardingResponse get(UUID userId, UUID roadmapId) {
        Roadmap roadmap = roadmapRepository
                .findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(this::onboardingNotFound);
        return response(roadmap);
    }

    @Override
    @Transactional
    public RoadmapOnboardingResponse save(
            UUID userId, UUID roadmapId, SaveRoadmapOnboardingRequest request) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        if (roadmap.getStatus() != RoadmapStatus.ONBOARDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Completed Roadmap onboarding cannot be edited.");
        }

        validateChoice(
                request.dailyCommitmentMinutes(),
                DAILY_COMMITMENT_OPTIONS,
                "Daily commitment must be 30, 60, or 120 minutes.");
        validateChoice(
                request.expectedDurationDays(),
                EXPECTED_DURATION_OPTIONS,
                "Expected duration must be 30, 60, or 90 days.");

        LearningSource goalSource = requireGoalSource(roadmapId);
        if (request.goal() != null) {
            goalSource.updateGoal(normalizeGoal(request.goal()));
        }
        roadmap.updateOnboarding(
                request.proficiencyLevel(),
                request.dailyCommitmentMinutes(),
                request.expectedDurationDays());
        learningSourceRepository.save(goalSource);
        roadmapRepository.saveAndFlush(roadmap);

        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_ONBOARDING_UPDATED,
                "Roadmap",
                roadmapId.toString());
        return RoadmapOnboardingResponse.from(roadmap, goalSource);
    }

    @Override
    @Transactional
    public RoadmapOnboardingResponse complete(UUID userId, UUID roadmapId) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        LearningSource goalSource = requireGoalSource(roadmapId);
        if (roadmap.isOnboardingComplete() && roadmap.getStatus() == RoadmapStatus.DRAFT) {
            return RoadmapOnboardingResponse.from(roadmap, goalSource);
        }
        if (roadmap.getStatus() != RoadmapStatus.ONBOARDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Roadmap is not in onboarding.");
        }
        requireCompleteSurvey(roadmap, goalSource);

        goalSource.markReady();
        roadmap.completeOnboarding(Instant.now().truncatedTo(ChronoUnit.MICROS));
        learningSourceRepository.save(goalSource);
        roadmapRepository.saveAndFlush(roadmap);
        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_ONBOARDING_COMPLETED,
                "Roadmap",
                roadmapId.toString());
        return RoadmapOnboardingResponse.from(roadmap, goalSource);
    }

    private UserAccount requireEligibleUserForUpdate(UUID userId) {
        UserAccount user = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));
        if (user.getRole() != UserRole.USER) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Only USER accounts can start Roadmap onboarding.");
        }
        boolean profileComplete = userProfileRepository
                .findByUserId(userId)
                .map(profile -> profile.isSetupCompleted())
                .orElse(false);
        if (!profileComplete) {
            throw new BusinessException(
                    ErrorCode.PROFILE_SETUP_REQUIRED,
                    "Complete the personal profile before starting Roadmap onboarding.");
        }
        return user;
    }

    private Roadmap requireOwnedForUpdate(UUID userId, UUID roadmapId) {
        return roadmapRepository
                .findOwnedByIdForUpdate(roadmapId, userId)
                .orElseThrow(this::onboardingNotFound);
    }

    private LearningSource requireGoalSource(UUID roadmapId) {
        return roadmapSourceRepository
                .findGoalSourceByRoadmapId(roadmapId)
                .map(RoadmapSource::getLearningSource)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "The Roadmap goal source is not initialized."));
    }

    private RoadmapOnboardingResponse response(Roadmap roadmap) {
        return RoadmapOnboardingResponse.from(
                roadmap, requireGoalSource(roadmap.getId()));
    }

    private void requireCompleteSurvey(Roadmap roadmap, LearningSource goalSource) {
        if (goalSource.getContentText() == null
                || goalSource.getContentText().isBlank()
                || roadmap.getProficiencyLevel() == null
                || roadmap.getDailyCommitmentMinutes() == null
                || roadmap.getExpectedDurationDays() == null) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_ONBOARDING_INCOMPLETE,
                    "Goal, proficiency, daily commitment, and expected duration are required.");
        }
    }

    private String normalizeGoal(String goal) {
        if (goal.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Goal must not be blank.");
        }
        return goal.trim();
    }

    private void validateChoice(Integer value, Set<Integer> allowed, String message) {
        if (value != null && !allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private BusinessException onboardingNotFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "Roadmap onboarding was not found.");
    }
}
