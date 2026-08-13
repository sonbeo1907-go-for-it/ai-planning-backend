package com.codegym.aiplanning.controller.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.material.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class MaterialControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StorageService storageService;

    @Test
    void uploadPdfHappyPath() throws Exception {
        UserAccount user = createUser("upload-pdf-user", "Upload PDF User");
        String accessToken = login(user.getUsername());

        // PDF magic bytes
        byte[] pdfContent = "%PDF-1.4\n%EOF".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfContent);

        doNothing().when(storageService).store(any(MultipartFile.class), anyString());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("test.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"));
    }

    @Test
    void uploadTxtHappyPath() throws Exception {
        UserAccount user = createUser("upload-txt-user", "Upload TXT User");
        String accessToken = login(user.getUsername());

        byte[] txtContent = "Hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", txtContent);

        doNothing().when(storageService).store(any(MultipartFile.class), anyString());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("test.txt"))
                .andExpect(jsonPath("$.data.contentType").value("text/plain"));
    }

    @Test
    void uploadDocxHappyPath() throws Exception {
        UserAccount user = createUser("upload-docx-user", "Upload DOCX User");
        String accessToken = login(user.getUsername());

        // A minimal valid ZIP header for DOCX
        byte[] docxContent = "PK\u0003\u0004 dummy docx content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxContent);

        doNothing().when(storageService).store(any(MultipartFile.class), anyString());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("test.docx"))
                .andExpect(jsonPath("$.data.contentType").value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void uploadInvalidExtension() throws Exception {
        UserAccount user = createUser("invalid-ext-user", "Invalid Ext User");
        String accessToken = login(user.getUsername());

        byte[] content = "some content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", content);

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadSpoofedMimeType() throws Exception {
        UserAccount user = createUser("spoof-user", "Spoof User");
        String accessToken = login(user.getUsername());

        // File named .pdf but content is actually a fake EXE/script without PDF magic bytes
        byte[] content = "MZ \n some executable content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", content);

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Detected content type is not allowed: application/x-msdownload"));
    }

    @Test
    void uploadAnonymous() throws Exception {
        byte[] txtContent = "Hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", txtContent);

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadEmptyFile() throws Exception {
        UserAccount user = createUser("empty-user", "Empty User");
        String accessToken = login(user.getUsername());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileField() throws Exception {
        UserAccount user = createUser("missing-field-user", "Missing Field User");
        String accessToken = login(user.getUsername());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTextHappyPath() throws Exception {
        UserAccount user = createUser("create-text-user", "Create Text User");
        String accessToken = login(user.getUsername());

        String content = "a".repeat(50); // Minimum length
        String requestJson = """
                {
                    "type": "TEXT",
                    "content": "%s"
                }
                """.formatted(content);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.content").value(content));
    }

    @Test
    void createGoalDescriptionHappyPath() throws Exception {
        UserAccount user = createUser("create-goal-user", "Create Goal User");
        String accessToken = login(user.getUsername());

        String content = "b".repeat(50000); // Maximum length
        String requestJson = """
                {
                    "type": "GOAL_DESCRIPTION",
                    "content": "%s"
                }
                """.formatted(content);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("GOAL_DESCRIPTION"))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void createTextTooShort() throws Exception {
        UserAccount user = createUser("text-short-user", "Text Short User");
        String accessToken = login(user.getUsername());

        String content = "a".repeat(49); // Too short
        String requestJson = """
                {
                    "type": "TEXT",
                    "content": "%s"
                }
                """.formatted(content);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTextTooLong() throws Exception {
        UserAccount user = createUser("text-long-user", "Text Long User");
        String accessToken = login(user.getUsername());

        String content = "a".repeat(50001); // Too long
        String requestJson = """
                {
                    "type": "TEXT",
                    "content": "%s"
                }
                """.formatted(content);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTextInvalidTypeFile() throws Exception {
        UserAccount user = createUser("text-file-user", "Text File User");
        String accessToken = login(user.getUsername());

        String content = "a".repeat(50);
        String requestJson = """
                {
                    "type": "FILE",
                    "content": "%s"
                }
                """.formatted(content);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyMaterialsHappyPath() throws Exception {
        UserAccount user = createUser("get-mats-user", "Get Mats User");
        String accessToken = login(user.getUsername());

        mockMvc.perform(get(ApiConstant.MATERIALS + "?page=0&size=10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMyMaterialsExplicitSort() throws Exception {
        UserAccount user = createUser("get-mats-sort", "Get Mats Sort User");
        String accessToken = login(user.getUsername());

        mockMvc.perform(get(ApiConstant.MATERIALS + "?page=0&size=10&sort=createdAt,desc")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private UserAccount createUser(String username, String fullName) {
        UserAccount account = createAccount(username, fullName, UserRole.USER);
        userProfileRepository.saveAndFlush(UserProfile.create(account));
        return account;
    }

    private UserAccount createAccount(String username, String fullName, UserRole role) {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                username,
                username + "@example.com",
                passwordEncoder.encode("Password@123"),
                fullName,
                role,
                AccountStatus.ACTIVE));
    }

    private String login(String username) throws Exception {
        String email = username + "@example.com";
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
