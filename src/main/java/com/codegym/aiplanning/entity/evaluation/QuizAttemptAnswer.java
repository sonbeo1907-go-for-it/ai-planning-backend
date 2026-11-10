package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_attempt_answers")
public class QuizAttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    private QuizQuestion question;

    @Column(name = "selected_option", nullable = false, length = 10)
    private String selectedOption;

    @Column(nullable = false)
    private boolean correct;

    protected QuizAttemptAnswer() {}

    public static QuizAttemptAnswer create(QuizQuestion question, String selectedOption) {
        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.question = question;
        answer.selectedOption = selectedOption;
        answer.correct = question.getCorrectOption().equalsIgnoreCase(selectedOption);
        return answer;
    }

    void setAttempt(QuizAttempt attempt) {
        this.attempt = attempt;
    }

    public QuizAttempt getAttempt() {
        return attempt;
    }

    public QuizQuestion getQuestion() {
        return question;
    }

    public String getSelectedOption() {
        return selectedOption;
    }

    public boolean isCorrect() {
        return correct;
    }
}
