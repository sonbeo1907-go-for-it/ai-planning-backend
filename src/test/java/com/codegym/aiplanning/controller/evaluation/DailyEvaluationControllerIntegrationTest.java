package com.codegym.aiplanning.controller.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.evaluation.dto.AnswerSubmissionDto;
import com.codegym.aiplanning.controller.evaluation.dto.SelfEvaluationRequest;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class DailyEvaluationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RoadmapRepository roadmapRepository;

    @Autowired
    private DailyPlanRepository dailyPlanRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldReturnBadRequestWhenNoCompletedTasksForQuizGeneration() throws Exception {
        UserAccount user = createUser("eval-user-1", "Eval User 1");
        String accessToken = login(user.getEmail());

        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.manualDraft(user, "Java Roadmap", "Description"));
        DailyPlan plan = dailyPlanRepository.saveAndFlush(DailyPlan.create(user.getId(), LocalDate.now(), "Asia/Ho_Chi_Minh", roadmap.getId()));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/quiz/generate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_COMPLETED_TASKS"));
    }

    @Test
    void shouldSubmitQuizAndCalculateScoreAndPassedStatus() throws Exception {
        UserAccount user = createUser("eval-user-2", "Eval User 2");
        String accessToken = login(user.getEmail());

        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.manualDraft(user, "Spring Roadmap", "Description"));
        DailyPlan plan = dailyPlanRepository.saveAndFlush(DailyPlan.create(user.getId(), LocalDate.now(), "Asia/Ho_Chi_Minh", roadmap.getId()));

        Quiz quiz = Quiz.createDailyMicroQuiz(user, plan, roadmap);
        QuizQuestion q1 = QuizQuestion.create(null, "Q1?", "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]", "A", "Giải thích Q1", 0);
        QuizQuestion q2 = QuizQuestion.create(null, "Q2?", "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]", "B", "Giải thích Q2", 1);
        QuizQuestion q3 = QuizQuestion.create(null, "Q3?", "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]", "A", "Giải thích Q3", 2);
        QuizQuestion q4 = QuizQuestion.create(null, "Q4?", "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]", "A", "Giải thích Q4", 3);
        QuizQuestion q5 = QuizQuestion.create(null, "Q5?", "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]", "B", "Giải thích Q5", 4);
        quiz.addQuestion(q1);
        quiz.addQuestion(q2);
        quiz.addQuestion(q3);
        quiz.addQuestion(q4);
        quiz.addQuestion(q5);
        quiz = quizRepository.saveAndFlush(quiz);

        // Submit 4 out of 5 correct (80.0% -> passed = true)
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                new AnswerSubmissionDto(quiz.getQuestions().get(0).getId(), "A"), // Correct
                new AnswerSubmissionDto(quiz.getQuestions().get(1).getId(), "B"), // Correct
                new AnswerSubmissionDto(quiz.getQuestions().get(2).getId(), "A"), // Correct
                new AnswerSubmissionDto(quiz.getQuestions().get(3).getId(), "A"), // Correct
                new AnswerSubmissionDto(quiz.getQuestions().get(4).getId(), "A")  // Wrong (correct is B)
        ));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.score").value(80.0))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.questions[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.questions[0].explanation").value("Giải thích Q1"))
                .andExpect(jsonPath("$.data.questions[4].isCorrect").value(false));

        // Submitting again should be rejected with 409 CONFLICT QUIZ_ALREADY_SUBMITTED
        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_ALREADY_SUBMITTED"));
    }

    @Test
    void shouldRecordSelfEvaluationAndRetrieveSummary() throws Exception {
        UserAccount user = createUser("eval-user-3", "Eval User 3");
        String accessToken = login(user.getEmail());

        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.manualDraft(user, "Roadmap 3", "Description"));
        DailyPlan plan = dailyPlanRepository.saveAndFlush(DailyPlan.create(user.getId(), LocalDate.now(), "Asia/Ho_Chi_Minh", roadmap.getId()));

        SelfEvaluationRequest evalRequest = new SelfEvaluationRequest(4, "Hiểu bài khá tốt, còn phần async chưa nắm vững.");

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/evaluation")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(4))
                .andExpect(jsonPath("$.data.feedbackNote").value("Hiểu bài khá tốt, còn phần async chưa nắm vững."));

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/evaluation")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(4));
    }

    private UserAccount createUser(String emailAlias, String displayName) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                emailAlias + "@example.com",
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, displayName));
        return account;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "email", email,
                                        "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
