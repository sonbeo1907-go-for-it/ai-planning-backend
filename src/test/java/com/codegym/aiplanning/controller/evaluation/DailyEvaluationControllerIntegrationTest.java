package com.codegym.aiplanning.controller.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private RoadmapRepository roadmapRepository;
    @Autowired private RoadmapVersionRepository roadmapVersionRepository;
    @Autowired private RoadmapItemRepository roadmapItemRepository;
    @Autowired private DailyPlanRepository dailyPlanRepository;
    @Autowired private DailyPlanVersionRepository dailyPlanVersionRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private WeakTopicRepository weakTopicRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void quizGenerationRequiresAnActiveVersionWithCompletedTasks() throws Exception {
        UserAccount user = createUser("eval-generation@example.com", "Evaluation User");
        String token = login(user.getEmail());
        Roadmap roadmap = roadmapRepository.saveAndFlush(
                Roadmap.manualDraft(user, "Java Roadmap", "Description"));
        DailyPlan plan = dailyPlanRepository.saveAndFlush(DailyPlan.create(
                user.getId(),
                LocalDate.now(),
                "Asia/Ho_Chi_Minh",
                roadmap.getId()));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + plan.getId() + "/quiz/generate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_COMPLETED_TASKS"));
    }

    @Test
    void quizSubmissionCreatesAStableAttemptAndRejectsASecondDailySubmission() throws Exception {
        EvaluationFixture fixture = createFixture("eval-submit@example.com");
        Quiz quiz = createFiveQuestionQuiz(fixture);
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                answer(quiz, 0, "A"),
                answer(quiz, 1, "B"),
                answer(quiz, 2, "A"),
                answer(quiz, 3, "A"),
                answer(quiz, 4, "A")));

        String path = ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                + "/quiz/" + quiz.getId() + "/submit";
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(80.0))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.dailyPlanVersionId")
                        .value(fixture.dailyPlanVersion().getId().toString()));

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_ALREADY_SUBMITTED"));
    }

    @Test
    void quizSubmissionRejectsIncompleteAnswers() throws Exception {
        EvaluationFixture fixture = createFixture("eval-incomplete@example.com");
        Quiz quiz = createFiveQuestionQuiz(fixture);
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(answer(quiz, 0, "A")));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUIZ_ANSWERS_INCOMPLETE"));
    }

    @Test
    void quizSubmissionRejectsDuplicateAnswers() throws Exception {
        EvaluationFixture fixture = createFixture("eval-duplicate@example.com");
        Quiz quiz = createFiveQuestionQuiz(fixture);
        AnswerSubmissionDto repeatedAnswer = answer(quiz, 0, "A");
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                repeatedAnswer,
                repeatedAnswer,
                repeatedAnswer,
                repeatedAnswer,
                repeatedAnswer));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUIZ_ANSWER_DUPLICATE"));
    }

    @Test
    void quizSubmissionRejectsAQuestionOutsideTheQuiz() throws Exception {
        EvaluationFixture fixture = createFixture("eval-foreign-question@example.com");
        Quiz quiz = createFiveQuestionQuiz(fixture);
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                new AnswerSubmissionDto(UUID.randomUUID(), "A"),
                answer(quiz, 1, "B"),
                answer(quiz, 2, "A"),
                answer(quiz, 3, "A"),
                answer(quiz, 4, "B")));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUIZ_QUESTION_NOT_FOUND"));
    }

    @Test
    void failedQuizCreatesAWeakTopicForTheExactRoadmapVersion() throws Exception {
        EvaluationFixture fixture = createFixture("eval-weak-topic@example.com");
        Quiz quiz = createFiveQuestionQuiz(fixture);
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                answer(quiz, 0, "B"),
                answer(quiz, 1, "A"),
                answer(quiz, 2, "B"),
                answer(quiz, 3, "B"),
                answer(quiz, 4, "A")));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/quiz/" + quiz.getId() + "/submit")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0.0));

        var weakTopic = weakTopicRepository
                .findByUserIdAndRoadmapItemId(
                        fixture.user().getId(),
                        fixture.topic().getId())
                .orElseThrow();
        assertThat(weakTopic.getRoadmapVersion().getId())
                .isEqualTo(fixture.roadmapVersion().getId());
    }

    @Test
    void selfEvaluationIsStoredAgainstTheExactActiveVersion() throws Exception {
        EvaluationFixture fixture = createFixture("eval-self@example.com");
        SelfEvaluationRequest request = new SelfEvaluationRequest(
                4,
                "I need more practice with asynchronous code.");

        String path = ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId() + "/evaluation";
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(4))
                .andExpect(jsonPath("$.data.dailyPlanVersionId")
                        .value(fixture.dailyPlanVersion().getId().toString()));

        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + fixture.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(4));
    }

    @Test
    void anotherUserCannotReadPersonalEvaluationData() throws Exception {
        EvaluationFixture fixture = createFixture("eval-owner@example.com");
        UserAccount anotherUser = createUser("eval-other@example.com", "Other User");

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/evaluation")
                        .header("Authorization", "Bearer " + login(anotherUser.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotAccessPersonalEvaluationEndpoints() throws Exception {
        EvaluationFixture fixture = createFixture("eval-user@example.com");
        UserAccount admin = createAccount(
                "eval-admin@example.com",
                "Evaluation Admin",
                UserRole.ADMIN);

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + fixture.plan().getId()
                        + "/evaluation")
                        .header("Authorization", "Bearer " + login(admin.getEmail())))
                .andExpect(status().isForbidden());
    }

    private Quiz createFiveQuestionQuiz(EvaluationFixture fixture) {
        Quiz quiz = Quiz.createDailyMicroQuiz(
                fixture.user(),
                fixture.plan(),
                fixture.dailyPlanVersion(),
                fixture.roadmap(),
                fixture.roadmapVersion());
        quiz.addQuestion(question(fixture.topic(), "Q1", "A", 0));
        quiz.addQuestion(question(fixture.topic(), "Q2", "B", 1));
        quiz.addQuestion(question(fixture.topic(), "Q3", "A", 2));
        quiz.addQuestion(question(fixture.topic(), "Q4", "A", 3));
        quiz.addQuestion(question(fixture.topic(), "Q5", "B", 4));
        return quizRepository.saveAndFlush(quiz);
    }

    private QuizQuestion question(
            RoadmapItem topic,
            String text,
            String correctOption,
            int order) {
        return QuizQuestion.create(
                topic,
                text,
                "[{\"key\":\"A\",\"text\":\"A\"},{\"key\":\"B\",\"text\":\"B\"}]",
                correctOption,
                "Explanation",
                order);
    }

    private AnswerSubmissionDto answer(Quiz quiz, int index, String option) {
        return new AnswerSubmissionDto(quiz.getQuestions().get(index).getId(), option);
    }

    private EvaluationFixture createFixture(String email) throws Exception {
        UserAccount user = createUser(email, "Evaluation User");
        Roadmap roadmap = roadmapRepository.saveAndFlush(
                Roadmap.manualDraft(user, "Roadmap", "Description"));
        RoadmapVersion roadmapVersion = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL));
        RoadmapItem milestone = roadmapItemRepository.saveAndFlush(
                RoadmapItem.milestone(roadmapVersion, "Week 1", "", 0));
        RoadmapItem topic = roadmapItemRepository.saveAndFlush(
                RoadmapItem.topic(roadmapVersion, milestone, "Topic", "", 0, 60));
        RoadmapItem learningUnit = roadmapItemRepository.saveAndFlush(
                RoadmapItem.learningUnit(
                        roadmapVersion,
                        topic,
                        "Apply the topic",
                        "",
                        0,
                        60));
        DailyPlan plan = dailyPlanRepository.saveAndFlush(DailyPlan.create(
                user.getId(),
                LocalDate.now(),
                "Asia/Ho_Chi_Minh",
                roadmap.getId()));
        DailyPlanVersion dailyVersion = dailyPlanVersionRepository.saveAndFlush(
                DailyPlanVersion.create(
                        plan.getId(),
                        1,
                        DailyPlanVersionOrigin.MANUAL,
                        60,
                        60));
        dailyVersion.activate(Instant.now());
        dailyPlanVersionRepository.saveAndFlush(dailyVersion);
        plan.activateVersion(dailyVersion.getId());
        dailyPlanRepository.saveAndFlush(plan);
        return new EvaluationFixture(
                user,
                login(email),
                roadmap,
                roadmapVersion,
                learningUnit,
                plan,
                dailyVersion);
    }

    private UserAccount createUser(String email, String displayName) {
        return createAccount(email, displayName, UserRole.USER);
    }

    private UserAccount createAccount(
            String email,
            String displayName,
            UserRole role) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, displayName));
        return account;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private record EvaluationFixture(
            UserAccount user,
            String token,
            Roadmap roadmap,
            RoadmapVersion roadmapVersion,
            RoadmapItem topic,
            DailyPlan plan,
            DailyPlanVersion dailyPlanVersion) {}
}
