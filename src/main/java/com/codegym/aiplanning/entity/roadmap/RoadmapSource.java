package com.codegym.aiplanning.entity.roadmap;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.source.LearningSource;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "roadmap_sources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_roadmap_sources_roadmap_source",
                columnNames = {"roadmap_id", "learning_source_id"}))
public class RoadmapSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learning_source_id", nullable = false)
    private LearningSource learningSource;

    protected RoadmapSource() {}

    public static RoadmapSource link(Roadmap roadmap, LearningSource learningSource) {
        if (!roadmap.getOwner().getId().equals(learningSource.getOwner().getId())) {
            throw new IllegalArgumentException("Roadmap and LearningSource owners must match.");
        }
        RoadmapSource link = new RoadmapSource();
        link.roadmap = roadmap;
        link.learningSource = learningSource;
        return link;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public LearningSource getLearningSource() {
        return learningSource;
    }
}
