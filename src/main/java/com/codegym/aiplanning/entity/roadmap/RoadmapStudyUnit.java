package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "roadmap_study_units")
public class RoadmapStudyUnit extends BaseEntity {

    @Column(name = "roadmap_version_id", nullable = false)
    private UUID roadmapVersionId;

    @Column(name = "roadmap_item_id", nullable = false)
    private UUID roadmapItemId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected RoadmapStudyUnit() {}

    public static RoadmapStudyUnit create(
            UUID roadmapVersionId,
            UUID roadmapItemId,
            String title,
            String description,
            int orderIndex) {
        RoadmapStudyUnit unit = new RoadmapStudyUnit();
        unit.roadmapVersionId = roadmapVersionId;
        unit.roadmapItemId = roadmapItemId;
        unit.title = title;
        unit.description = description;
        unit.orderIndex = orderIndex;
        return unit;
    }

    public UUID getRoadmapVersionId() {
        return roadmapVersionId;
    }

    public UUID getRoadmapItemId() {
        return roadmapItemId;
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

    public void update(String title, String description, int orderIndex) {
        this.title = title;
        this.description = description;
        this.orderIndex = orderIndex;
    }
}
