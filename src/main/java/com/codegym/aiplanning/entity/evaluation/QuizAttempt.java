package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "quiz_attempts")
public class QuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuizAttemptAnswer> answers = new ArrayList<>();

    protected QuizAttempt() {}

    public static QuizAttempt create(
            Quiz quiz,
            UserAccount user,
            int attemptNumber,
            BigDecimal score,
            boolean passed,
            Instant submittedAt) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.quiz = quiz;
        attempt.user = user;
        attempt.attemptNumber = attemptNumber;
        attempt.score = score;
        attempt.passed = passed;
        attempt.submittedAt = submittedAt;
        return attempt;
    }

    public void addAnswer(QuizAttemptAnswer answer) {
        answers.add(answer);
        answer.setAttempt(this);
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public UserAccount getUser() {
        return user;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public BigDecimal getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<QuizAttemptAnswer> getAnswers() {
        return answers;
    }
}
