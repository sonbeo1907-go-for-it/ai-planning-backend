package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_plan_version_id")
    private DailyPlanVersion dailyPlanVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_version_id", nullable = false)
    private RoadmapVersion roadmapVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_type", nullable = false, length = 30)
    private QuizType quizType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_weak_topic_id")
    private WeakTopic targetWeakTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuizStatus status;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    protected Quiz() {}

    public static Quiz createDailyMicroQuiz(
            UserAccount user,
            DailyPlan dailyPlan,
            DailyPlanVersion dailyPlanVersion,
            Roadmap roadmap,
            RoadmapVersion roadmapVersion) {
        Quiz quiz = new Quiz();
        quiz.user = user;
        quiz.dailyPlan = dailyPlan;
        quiz.dailyPlanVersion = dailyPlanVersion;
        quiz.roadmap = roadmap;
        quiz.roadmapVersion = roadmapVersion;
        quiz.quizType = QuizType.DAILY_MICRO_QUIZ;
        quiz.status = QuizStatus.GENERATED;
        return quiz;
    }

    public static Quiz createMasteryCheck(
            UserAccount user,
            Roadmap roadmap,
            RoadmapVersion roadmapVersion,
            WeakTopic targetWeakTopic) {
        Quiz quiz = new Quiz();
        quiz.user = user;
        quiz.roadmap = roadmap;
        quiz.roadmapVersion = roadmapVersion;
        quiz.quizType = QuizType.MASTERY_CHECK;
        quiz.targetWeakTopic = targetWeakTopic;
        quiz.status = QuizStatus.GENERATED;
        return quiz;
    }

    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setQuiz(this);
    }

    public void markSubmitted() {
        this.status = QuizStatus.SUBMITTED;
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

    public DailyPlanVersion getDailyPlanVersion() {
        return dailyPlanVersion;
    }

    public RoadmapVersion getRoadmapVersion() {
        return roadmapVersion;
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

    public List<QuizQuestion> getQuestions() {
        return questions;
    }
}
