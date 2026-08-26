package com.codegym.aiplanning.entity.evaluation;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_item_id")
    private RoadmapItem roadmapItem;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "correct_option", nullable = false, length = 10)
    private String correctOption;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String explanation;

    @Column(name = "user_answer", length = 10)
    private String userAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected QuizQuestion() {}

    public static QuizQuestion create(
            RoadmapItem roadmapItem,
            String questionText,
            String optionsJson,
            String correctOption,
            String explanation,
            int orderIndex) {
        QuizQuestion question = new QuizQuestion();
        question.roadmapItem = roadmapItem;
        question.questionText = questionText;
        question.optionsJson = optionsJson;
        question.correctOption = correctOption;
        question.explanation = explanation;
        question.orderIndex = orderIndex;
        return question;
    }

    public void answer(String userAnswer) {
        this.userAnswer = userAnswer;
        this.isCorrect = this.correctOption.equalsIgnoreCase(userAnswer != null ? userAnswer.trim() : "");
    }

    void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public RoadmapItem getRoadmapItem() {
        return roadmapItem;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public String getCorrectOption() {
        return correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
