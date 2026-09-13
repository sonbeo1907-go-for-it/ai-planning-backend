package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "roadmap_items")
public class RoadmapItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_version_id", nullable = false)
    private RoadmapVersion roadmapVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_item_id")
    private RoadmapItem parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_item_type", length = 30)
    private RoadmapItemType parentItemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private RoadmapItemType itemType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "lineage_id")
    private UUID lineageId;

    protected RoadmapItem() {}

    public static RoadmapItem milestone(
            RoadmapVersion version,
            String title,
            String description,
            int orderIndex) {
        RoadmapItem item = new RoadmapItem();
        item.roadmapVersion = version;
        item.itemType = RoadmapItemType.MILESTONE;
        item.parentItemType = null;
        item.title = title;
        item.description = description;
        item.orderIndex = orderIndex;
        return item;
    }

    public static RoadmapItem topic(
            RoadmapVersion version,
            RoadmapItem milestone,
            String title,
            String description,
            int orderIndex,
            int estimatedMinutes) {
        requireParent(version, milestone, RoadmapItemType.MILESTONE, "Topic");
        RoadmapItem item = new RoadmapItem();
        item.roadmapVersion = version;
        item.parent = milestone;
        item.parentItemType = RoadmapItemType.MILESTONE;
        item.itemType = RoadmapItemType.TOPIC;
        item.title = title;
        item.description = description;
        item.orderIndex = orderIndex;
        item.estimatedMinutes = estimatedMinutes;
        return item;
    }

    public static RoadmapItem learningUnit(
            RoadmapVersion version,
            RoadmapItem topic,
            String title,
            String description,
            int orderIndex,
            int estimatedMinutes) {
        return learningUnit(
                version,
                topic,
                title,
                description,
                orderIndex,
                estimatedMinutes,
                UUID.randomUUID());
    }

    public static RoadmapItem learningUnit(
            RoadmapVersion version,
            RoadmapItem topic,
            String title,
            String description,
            int orderIndex,
            int estimatedMinutes,
            UUID lineageId) {
        requireParent(version, topic, RoadmapItemType.TOPIC, "Learning Unit");
        RoadmapItem item = new RoadmapItem();
        item.roadmapVersion = version;
        item.parent = topic;
        item.parentItemType = RoadmapItemType.TOPIC;
        item.itemType = RoadmapItemType.LEARNING_UNIT;
        item.title = title;
        item.description = description;
        item.orderIndex = orderIndex;
        item.estimatedMinutes = estimatedMinutes;
        item.lineageId = lineageId != null ? lineageId : UUID.randomUUID();
        return item;
    }

    public RoadmapVersion getRoadmapVersion() {
        return roadmapVersion;
    }

    public RoadmapItem getParent() {
        return parent;
    }

    public RoadmapItemType getItemType() {
        return itemType;
    }

    public RoadmapItemType getParentItemType() {
        return parentItemType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public UUID getLineageId() {
        return lineageId;
    }

    public void update(
            String title, String description, int orderIndex, Integer estimatedMinutes) {
        if (itemType == RoadmapItemType.LEARNING_UNIT
                && learningContentChanged(title, description)) {
            lineageId = UUID.randomUUID();
        }
        this.title = title;
        this.description = description;
        this.orderIndex = orderIndex;
        if (itemType == RoadmapItemType.TOPIC
                || itemType == RoadmapItemType.LEARNING_UNIT) {
            this.estimatedMinutes = estimatedMinutes;
        }
    }

    private boolean learningContentChanged(String newTitle, String newDescription) {
        return !sameNormalizedText(title, newTitle)
                || !sameNormalizedText(description, newDescription);
    }

    private boolean sameNormalizedText(String first, String second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.strip().equalsIgnoreCase(second.strip());
    }

    private static void requireParent(
            RoadmapVersion version,
            RoadmapItem parent,
            RoadmapItemType requiredType,
            String childLabel) {
        if (version == null || parent == null || parent.getItemType() != requiredType) {
            throw new IllegalArgumentException(
                    "A " + childLabel + " must belong to a " + requiredType + ".");
        }
        RoadmapVersion parentVersion = parent.getRoadmapVersion();
        boolean samePersistedVersion = parentVersion != null
                && parentVersion.getId() != null
                && parentVersion.getId().equals(version.getId());
        if (parentVersion != version && !samePersistedVersion) {
            throw new IllegalArgumentException(
                    "A " + childLabel + " and its parent must belong to the same Roadmap version.");
        }
    }

    public void moveTo(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
