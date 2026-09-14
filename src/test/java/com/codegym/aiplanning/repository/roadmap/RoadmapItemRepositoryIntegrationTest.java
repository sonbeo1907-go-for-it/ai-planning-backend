package com.codegym.aiplanning.repository.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class RoadmapItemRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoadmapRepository roadmapRepository;

    @Autowired
    private RoadmapVersionRepository roadmapVersionRepository;

    @Autowired
    private RoadmapItemRepository roadmapItemRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void ownedLearningUnitQueryFetchesTopicAndMilestoneOutsidePersistenceScope() {
        PersistedHierarchy hierarchy = transactionTemplate.execute(status -> {
            UserAccount owner = userAccountRepository.save(UserAccount.create(
                    "quiz-hierarchy-" + UUID.randomUUID() + "@test.com",
                    "hash",
                    UserRole.USER,
                    AccountStatus.ACTIVE));
            Roadmap roadmap = roadmapRepository.save(
                    Roadmap.manualDraft(owner, "Java", null));
            RoadmapVersion version = roadmapVersionRepository.save(
                    RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL));
            RoadmapItem milestone = roadmapItemRepository.save(
                    RoadmapItem.milestone(version, "Week 1", null, 0));
            RoadmapItem topic = roadmapItemRepository.save(
                    RoadmapItem.topic(
                            version,
                            milestone,
                            "Object-oriented programming",
                            null,
                            0,
                            120));
            RoadmapItem learningUnit = roadmapItemRepository.save(
                    RoadmapItem.learningUnit(
                            version,
                            topic,
                            "Encapsulation",
                            null,
                            0,
                            30));

            roadmapItemRepository.flush();
            return new PersistedHierarchy(owner.getId(), learningUnit.getId());
        });

        List<RoadmapItem> loaded = roadmapItemRepository.findAllOwnedByIdsWithHierarchy(
                List.of(hierarchy.learningUnitId()),
                hierarchy.ownerId());

        assertThat(loaded).hasSize(1);
        RoadmapItem learningUnit = loaded.get(0);
        assertThat(learningUnit.getTitle()).isEqualTo("Encapsulation");
        assertThat(learningUnit.getParent().getTitle())
                .isEqualTo("Object-oriented programming");
        assertThat(learningUnit.getParent().getParent().getTitle())
                .isEqualTo("Week 1");

        List<RoadmapItem> inaccessible = roadmapItemRepository
                .findAllOwnedByIdsWithHierarchy(
                        List.of(hierarchy.learningUnitId()),
                        UUID.randomUUID());
        assertThat(inaccessible).isEmpty();
    }

    private record PersistedHierarchy(UUID ownerId, UUID learningUnitId) {}
}
