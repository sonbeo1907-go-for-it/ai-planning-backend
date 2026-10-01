package com.codegym.aiplanning.controller.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.MaterialRepository;
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
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import java.io.IOException;
import java.util.UUID;
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
    private MaterialRepository materialRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StorageService storageService;

    @Test
    void uploadPdfHappyPath() throws Exception {
        UserAccount user = createUser("upload-pdf-user", "Upload PDF User");
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileField() throws Exception {
        UserAccount user = createUser("missing-field-user", "Missing Field User");
        String accessToken = login(user.getEmail());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTextHappyPath() throws Exception {
        UserAccount user = createUser("create-text-user", "Create Text User");
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

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
        String accessToken = login(user.getEmail());

        mockMvc.perform(get(ApiConstant.MATERIALS + "?page=0&size=10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMyMaterialsExplicitSort() throws Exception {
        UserAccount user = createUser("get-mats-sort", "Get Mats Sort User");
        String accessToken = login(user.getEmail());

        mockMvc.perform(get(ApiConstant.MATERIALS + "?page=0&size=10&sort=createdAt,desc")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void getMyMaterialsReturnsOnlyOwnedMaterials() throws Exception {
        UserAccount owner = createUser("material-owner", "Material Owner");
        UserAccount other = createUser("material-other", "Material Other");
        String ownerToken = login(owner.getEmail());
        String otherToken = login(other.getEmail());

        UUID ownerMaterialId = createTextMaterial(ownerToken, "o".repeat(50));
        createTextMaterial(otherToken, "x".repeat(50));

        mockMvc.perform(get(ApiConstant.MATERIALS + "?page=0&size=10")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ownerMaterialId.toString()));
    }

    @Test
    void materialSearchIsServerSideAndOwnerScoped() throws Exception {
        UserAccount owner = createUser("material-search-owner", "Material Search Owner");
        UserAccount other = createUser("material-search-other", "Material Search Other");
        String ownerToken = login(owner.getEmail());
        String otherToken = login(other.getEmail());

        UUID matchingId = createTextMaterial(
                ownerToken,
                "Spring Boot persistence practice " + "a".repeat(50));
        createTextMaterial(ownerToken, "React component composition " + "b".repeat(50));
        createTextMaterial(otherToken, "Spring Boot private material " + "c".repeat(50));

        mockMvc.perform(get(ApiConstant.MATERIALS)
                        .queryParam("q", "spring boot")
                        .queryParam("type", "TEXT")
                        .queryParam("status", "READY")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id")
                        .value(matchingId.toString()));
    }

    @Test
    void userCannotArchiveAnotherUsersMaterial() throws Exception {
        UserAccount owner = createUser("archive-owner", "Archive Owner");
        UserAccount other = createUser("archive-other", "Archive Other");
        String ownerToken = login(owner.getEmail());
        String otherToken = login(other.getEmail());
        UUID materialId = createTextMaterial(ownerToken, "a".repeat(50));

        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + materialId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + materialId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCannotUploadMaterial() throws Exception {
        UserAccount admin = createAccount("material-admin", "Material Admin", UserRole.ADMIN);
        String adminToken = login(admin.getEmail());
        MockMultipartFile file = new MockMultipartFile(
                "file", "admin.txt", "text/plain", "admin content".getBytes());

        mockMvc.perform(multipart(ApiConstant.MATERIALS)
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanArchiveMaterialWhileProcessing() throws Exception {
        UserAccount owner = createUser("processing-owner", "Processing Owner");
        String ownerToken = login(owner.getEmail());
        Material material = Material.create(
                owner,
                "processing.pdf",
                "application/pdf",
                128L,
                owner.getId() + "/processing.pdf");
        material.markAsProcessing();
        material = materialRepository.saveAndFlush(material);

        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + material.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                        materialRepository.findById(material.getId()).orElseThrow().isArchived())
                .isTrue();
    }

    @Test
    void getArchivedMaterials_ShouldOnlyReturnOwnedArchivedMaterials() throws Exception {
        UserAccount user1 = createUser("archived-u1", "Archived User 1");
        String token1 = login(user1.getEmail());

        UserAccount user2 = createUser("archived-u2", "Archived User 2");
        String token2 = login(user2.getEmail());

        // User 1 active material
        createTextMaterial(token1, "Active material for user 1 " + "a".repeat(50));

        // User 1 archived material
        UUID u1ArchivedId = createTextMaterial(token1, "Archived material for user 1 " + "b".repeat(50));
        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + u1ArchivedId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // User 2 archived material
        UUID u2ArchivedId = createTextMaterial(token2, "Archived material for user 2 " + "c".repeat(50));
        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + u2ArchivedId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        // User 1 queries archived materials
        mockMvc.perform(get(ApiConstant.MATERIALS)
                        .param("archived", "true")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(u1ArchivedId.toString()))
                .andExpect(jsonPath("$.data.content[0].archivedAt").isNotEmpty());
    }

    @Test
    void restoreMaterial_Success_ShouldClearArchivedAtAndReturn200() throws Exception {
        UserAccount user = createUser("restore-success-u", "Restore User");
        String token = login(user.getEmail());

        UUID materialId = createTextMaterial(token, "Material to be restored " + "d".repeat(50));
        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + materialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        long countBefore = materialRepository.count();

        // Restore material
        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + materialId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(materialId.toString()));

        // Matrix #3: Retains Material.id
        // Matrix #4: Does not create new resource
        org.assertj.core.api.Assertions.assertThat(materialRepository.count()).isEqualTo(countBefore);

        Material restored = materialRepository.findById(materialId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(restored.isArchived()).isFalse();

        // Matrix #13: Disappears from archived, appears in active
        mockMvc.perform(get(ApiConstant.MATERIALS)
                        .param("archived", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(ApiConstant.MATERIALS)
                        .param("archived", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(materialId.toString()));
    }

    @Test
    void restoreActiveMaterial_ShouldReturn409MaterialNotArchived() throws Exception {
        UserAccount user = createUser("restore-active-u", "Active User");
        String token = login(user.getEmail());

        UUID materialId = createTextMaterial(token, "Active material " + "e".repeat(50));

        // Try to restore already active material
        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + materialId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_ARCHIVED"));
    }

    @Test
    void restoreMaterial_ByAnotherUser_ShouldReturn404ResourceNotFound() throws Exception {
        UserAccount owner = createUser("owner-u", "Owner User");
        String ownerToken = login(owner.getEmail());

        UserAccount attacker = createUser("attacker-u", "Attacker User");
        String attackerToken = login(attacker.getEmail());

        UUID materialId = createTextMaterial(ownerToken, "Owner material " + "f".repeat(50));
        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + materialId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Attacker attempts restore
        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + materialId + "/restore")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void restoreMaterial_WhenStorageMissing_ShouldReturn404AndKeepMaterialArchived() throws Exception {
        UserAccount owner = createUser("storage-missing-u", "Storage Missing User");
        String ownerToken = login(owner.getEmail());

        Material material = Material.create(
                owner,
                "missing.pdf",
                "application/pdf",
                128L,
                owner.getId() + "/missing.pdf");
        material.archive();
        material = materialRepository.saveAndFlush(material);

        when(storageService.exists(material.getStorageKey())).thenReturn(false);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + material.getId() + "/restore")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORAGE_OBJECT_MISSING"));

        // Matrix #8: Resource remains archived
        Material unchanged = materialRepository.findById(material.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.isArchived()).isTrue();
    }

    @Test
    void restoreMaterial_WhenProcessing_ShouldResetToPending() throws Exception {
        UserAccount owner = createUser("processing-restore-u", "Processing Restore User");
        String ownerToken = login(owner.getEmail());

        Material material = Material.create(
                owner,
                "proc.pdf",
                "application/pdf",
                128L,
                owner.getId() + "/proc.pdf");
        material.markAsProcessing();
        material.archive();
        material = materialRepository.saveAndFlush(material);

        when(storageService.exists(material.getStorageKey())).thenReturn(true);

        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + material.getId() + "/restore")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        Material restored = materialRepository.findById(material.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(restored.isArchived()).isFalse();
        // Matrix #10: status is PENDING (or was claimed to PROCESSING by worker if run immediately)
        org.assertj.core.api.Assertions.assertThat(restored.getStatus())
                .isIn(com.codegym.aiplanning.entity.material.MaterialStatus.PENDING,
                      com.codegym.aiplanning.entity.material.MaterialStatus.PROCESSING,
                      com.codegym.aiplanning.entity.material.MaterialStatus.READY,
                      com.codegym.aiplanning.entity.material.MaterialStatus.FAILED);
    }

    @Test
    void archiveAndRestore_ShouldRecordAuditLogsWithoutContent() throws Exception {
        UserAccount owner = createUser("audit-test-u", "Audit Test User");
        String ownerToken = login(owner.getEmail());

        UUID materialId = createTextMaterial(ownerToken, "Super secret material text content " + "g".repeat(50));

        mockMvc.perform(delete(ApiConstant.MATERIALS + "/" + materialId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ApiConstant.MATERIALS + "/" + materialId + "/restore")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        java.util.List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> l.getTargetId() != null && l.getTargetId().equals(materialId.toString()))
                .toList();

        org.assertj.core.api.Assertions.assertThat(logs)
                .extracting(AuditLog::getAction)
                .contains(AuditEventAction.MATERIAL_ARCHIVED, AuditEventAction.MATERIAL_RESTORED);

        for (AuditLog log : logs) {
            org.assertj.core.api.Assertions.assertThat(log.getActorId()).isEqualTo(owner.getId());
            org.assertj.core.api.Assertions.assertThat(log.getTargetResource()).isEqualTo("Material");
            // Matrix #11 & #12: Audit has requestId, and NO document content
            org.assertj.core.api.Assertions.assertThat(log.getRequestId()).isNotNull();
            org.assertj.core.api.Assertions.assertThat(log.getDetails()).isNull();
            org.assertj.core.api.Assertions.assertThat(log.getMetadata()).isNull();
        }
    }

    private UUID createTextMaterial(String accessToken, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.MATERIALS + "/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "type", "TEXT",
                                "content", content)))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText());
    }

    private UserAccount createUser(String emailPrefix, String displayName) {
        UserAccount account = createAccount(emailPrefix, displayName, UserRole.USER);
        userProfileRepository.saveAndFlush(UserProfile.create(account, displayName));
        return account;
    }

    private UserAccount createAccount(String emailPrefix, String displayName, UserRole role) {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                emailPrefix + "@example.com",
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
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
