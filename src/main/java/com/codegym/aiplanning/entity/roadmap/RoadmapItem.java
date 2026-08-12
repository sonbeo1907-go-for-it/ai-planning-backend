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

    protected RoadmapItem() {}

    public static RoadmapItem milestone(
            RoadmapVersion version,
            String title,
            String description,
            int orderIndex) {
        RoadmapItem item = new RoadmapItem();
        item.roadmapVersion = version;
        item.itemType = RoadmapItemType.MILESTONE;
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
        RoadmapItem item = new RoadmapItem();
        item.roadmapVersion = version;
        item.parent = milestone;
        item.itemType = RoadmapItemType.TOPIC;
        item.title = title;
        item.description = description;
        item.orderIndex = orderIndex;
        item.estimatedMinutes = estimatedMinutes;
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

    public void update(
            String title, String description, int orderIndex, Integer estimatedMinutes) {
        this.title = title;
        this.description = description;
        this.orderIndex = orderIndex;
        if (itemType == RoadmapItemType.TOPIC) {
            this.estimatedMinutes = estimatedMinutes;
        }
    }

    public void moveTo(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
