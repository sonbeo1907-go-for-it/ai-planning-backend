package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_evaluations")
public class DailyEvaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_id", nullable = false, unique = true)
    private DailyPlan dailyPlan;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @Column(name = "quiz_score", precision = 5, scale = 2)
    private BigDecimal quizScore;

    @Column(name = "quiz_passed")
    private Boolean quizPassed;

    @Column(name = "overall_rating")
    private Integer overallRating;

    @Column(name = "feedback_note", columnDefinition = "TEXT")
    private String feedbackNote;

    protected DailyEvaluation() {}

    public static DailyEvaluation create(
            UserAccount user,
            DailyPlan dailyPlan,
            LocalDate evaluationDate,
            BigDecimal quizScore,
            Boolean quizPassed,
            Integer overallRating,
            String feedbackNote) {
        DailyEvaluation evaluation = new DailyEvaluation();
        evaluation.user = user;
        evaluation.dailyPlan = dailyPlan;
        evaluation.evaluationDate = evaluationDate;
        evaluation.quizScore = quizScore;
        evaluation.quizPassed = quizPassed;
        evaluation.overallRating = overallRating;
        evaluation.feedbackNote = feedbackNote;
        return evaluation;
    }

    public void updateQuizResult(BigDecimal quizScore, boolean quizPassed) {
        this.quizScore = quizScore;
        this.quizPassed = quizPassed;
    }

    public void updateSelfRating(Integer overallRating, String feedbackNote) {
        this.overallRating = overallRating;
        this.feedbackNote = feedbackNote;
    }

    public UserAccount getUser() {
        return user;
    }

    public DailyPlan getDailyPlan() {
        return dailyPlan;
    }

    public LocalDate getEvaluationDate() {
        return evaluationDate;
    }

    public BigDecimal getQuizScore() {
        return quizScore;
    }

    public Boolean getQuizPassed() {
        return quizPassed;
    }

    public Integer getOverallRating() {
        return overallRating;
    }

    public String getFeedbackNote() {
        return feedbackNote;
    }
}
