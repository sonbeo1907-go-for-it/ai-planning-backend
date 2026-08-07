package com.codegym.aiplanning.controller.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.curriculum.Module;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.curriculum.ModuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class ModuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    private String adminToken;
    private Course course;

    @BeforeEach
    void setUp() throws Exception {
        moduleRepository.deleteAll();

        // Login as admin
        String loginBody = """
                {
                    "email": "admin@aiplanning.local",
                    "password": "Admin@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        adminToken = jsonNode.get("data").get("accessToken").asText();

        String uniqueCourseCode = "COURSE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        course = courseRepository.save(Course.create(uniqueCourseCode, "Test Course", "Java Course", UUID.randomUUID()));
    }

    @Test
    void createModule_success() throws Exception {
        String payload = """
                {
                    "code": "JAVA_CORE",
                    "name": "Java Core Basic",
                    "description": "Basic Java Syntax & OOP",
                    "sequenceNumber": 1
                }
                """;

        mockMvc.perform(post(ApiConstant.COURSES + "/" + course.getId() + "/modules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("JAVA_CORE"))
                .andExpect(jsonPath("$.data.name").value("Java Core Basic"))
                .andExpect(jsonPath("$.data.sequenceNumber").value(1));

        assertThat(moduleRepository.existsByCourseIdAndCode(course.getId(), "JAVA_CORE")).isTrue();
    }

    @Test
    void createModule_duplicateCode_returnsConflict() throws Exception {
        moduleRepository.save(Module.create(course.getId(), "JAVA_CORE", "Java Core", "Desc", 1, UUID.randomUUID()));

        String payload = """
                {
                    "code": "JAVA_CORE",
                    "name": "Another Java Core",
                    "description": "Desc"
                }
                """;

        mockMvc.perform(post(ApiConstant.COURSES + "/" + course.getId() + "/modules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void getModules_returnsList() throws Exception {
        moduleRepository.save(Module.create(course.getId(), "M1", "Module 1", "Desc", 1, UUID.randomUUID()));
        moduleRepository.save(Module.create(course.getId(), "M2", "Module 2", "Desc", 2, UUID.randomUUID()));

        mockMvc.perform(get(ApiConstant.COURSES + "/" + course.getId() + "/modules")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("M1"))
                .andExpect(jsonPath("$.data[1].code").value("M2"));
    }

    @Test
    void updateModule_success() throws Exception {
        Module existing = moduleRepository.save(Module.create(course.getId(), "M1", "Old Name", "Old Desc", 1, UUID.randomUUID()));

        String payload = """
                {
                    "name": "Updated Name",
                    "description": "Updated Description"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(ApiConstant.COURSES + "/" + course.getId() + "/modules/" + existing.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.description").value("Updated Description"));
    }

    @Test
    void reorderModules_success() throws Exception {
        Module m1 = moduleRepository.save(Module.create(course.getId(), "M1", "Mod 1", "Desc", 1, UUID.randomUUID()));
        Module m2 = moduleRepository.save(Module.create(course.getId(), "M2", "Mod 2", "Desc", 2, UUID.randomUUID()));

        String payload = String.format("""
                {
                    "items": [
                        { "moduleId": "%s", "sequenceNumber": 2 },
                        { "moduleId": "%s", "sequenceNumber": 1 }
                    ]
                }
                """, m1.getId(), m2.getId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(ApiConstant.COURSES + "/" + course.getId() + "/modules/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("M2"))
                .andExpect(jsonPath("$.data[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.data[1].code").value("M1"))
                .andExpect(jsonPath("$.data[1].sequenceNumber").value(2));
    }

    @Test
    void deactivateModule_success() throws Exception {
        Module existing = moduleRepository.save(Module.create(course.getId(), "M_DEACT", "Deact Name", "Desc", 1, UUID.randomUUID()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ApiConstant.COURSES + "/" + course.getId() + "/modules/" + existing.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void activateModule_success() throws Exception {
        Module existing = Module.create(course.getId(), "M_ACT", "Act Name", "Desc", 1, UUID.randomUUID());
        existing.deactivate(UUID.randomUUID());
        existing = moduleRepository.save(existing);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ApiConstant.COURSES + "/" + course.getId() + "/modules/" + existing.getId() + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
