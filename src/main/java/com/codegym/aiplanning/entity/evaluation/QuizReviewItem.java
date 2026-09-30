package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_review_items")
public class QuizReviewItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_item_id", nullable = false)
    private DailyPlanItem dailyPlanItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_item_id", nullable = false)
    private RoadmapItem roadmapItem;

    protected QuizReviewItem() {}

    public static QuizReviewItem create(Quiz quiz, DailyPlanItem dailyPlanItem, RoadmapItem roadmapItem) {
        QuizReviewItem item = new QuizReviewItem();
        item.quiz = quiz;
        item.dailyPlanItem = dailyPlanItem;
        item.roadmapItem = roadmapItem;
        return item;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public DailyPlanItem getDailyPlanItem() {
        return dailyPlanItem;
    }

    public RoadmapItem getRoadmapItem() {
        return roadmapItem;
    }
}
