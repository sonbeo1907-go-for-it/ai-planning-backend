package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
public class Quiz extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_plan_id")
    private DailyPlan dailyPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_type", nullable = false, length = 30)
    private QuizType quizType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_weak_topic_id")
    private WeakTopic targetWeakTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuizStatus status;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    private Boolean passed;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    protected Quiz() {}

    public static Quiz createDailyMicroQuiz(
            UserAccount user,
            DailyPlan dailyPlan,
            Roadmap roadmap) {
        Quiz quiz = new Quiz();
        quiz.user = user;
        quiz.dailyPlan = dailyPlan;
        quiz.roadmap = roadmap;
        quiz.quizType = QuizType.DAILY_MICRO_QUIZ;
        quiz.status = QuizStatus.GENERATED;
        return quiz;
    }

    public static Quiz createMasteryCheck(
            UserAccount user,
            DailyPlan dailyPlan,
            Roadmap roadmap,
            WeakTopic targetWeakTopic) {
        Quiz quiz = new Quiz();
        quiz.user = user;
        quiz.dailyPlan = dailyPlan;
        quiz.roadmap = roadmap;
        quiz.quizType = QuizType.MASTERY_CHECK;
        quiz.targetWeakTopic = targetWeakTopic;
        quiz.status = QuizStatus.GENERATED;
        return quiz;
    }

    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setQuiz(this);
    }

    public void completeSubmission(BigDecimal calculatedScore, boolean isPassed, Instant submissionTime) {
        this.score = calculatedScore;
        this.passed = isPassed;
        this.status = QuizStatus.SUBMITTED;
        this.submittedAt = submissionTime != null ? submissionTime : Instant.now();
    }

    public UserAccount getUser() {
        return user;
    }

    public DailyPlan getDailyPlan() {
        return dailyPlan;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public QuizType getQuizType() {
        return quizType;
    }

    public WeakTopic getTargetWeakTopic() {
        return targetWeakTopic;
    }

    public QuizStatus getStatus() {
        return status;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Boolean getPassed() {
        return passed;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<QuizQuestion> getQuestions() {
        return questions;
    }
}
