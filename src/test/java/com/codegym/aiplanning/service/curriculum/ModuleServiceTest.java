package com.codegym.aiplanning.service.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.curriculum.dto.CreateModuleRequest;
import com.codegym.aiplanning.controller.curriculum.dto.ModuleResponse;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.curriculum.Module;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.repository.curriculum.ModuleRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.curriculum.impl.ModuleServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AuditLogService auditLogService;

    private ModuleServiceImpl moduleService;

    private UUID courseId;
    private UUID adminId;
    private Jwt adminJwt;
    private Course course;

    @BeforeEach
    void setUp() {
        moduleService = new ModuleServiceImpl(moduleRepository, courseRepository, auditLogService);
        courseId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        adminJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", adminId.toString())
                .claim("username", "admin@example.com")
                .build();
        course = Course.create("JAVA_CORE", "Fullstack Java", "Desc", adminId);
    }

    @Test
    void createModule_success_autoCalculatesSequenceNumber() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(moduleRepository.existsByCourseIdAndCode(courseId, "JAVA_CORE")).thenReturn(false);
        when(moduleRepository.findMaxSequenceNumberByCourseId(courseId)).thenReturn(Optional.of(2));
        when(moduleRepository.save(any(Module.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateModuleRequest req = new CreateModuleRequest("java_core", "Java Core Basic", "Module 1", null);

        ModuleResponse res = moduleService.createModule(courseId, req, adminJwt);

        assertThat(res.code()).isEqualTo("JAVA_CORE");
        assertThat(res.name()).isEqualTo("Java Core Basic");
        assertThat(res.sequenceNumber()).isEqualTo(3);

        ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
        verify(moduleRepository).save(captor.capture());
        assertThat(captor.getValue().getCourseId()).isEqualTo(courseId);
    }

    @Test
    void createModule_throws_whenCourseNotFound() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        CreateModuleRequest req = new CreateModuleRequest("JAVA_CORE", "Java Core", "Desc", 1);

        assertThatThrownBy(() -> moduleService.createModule(courseId, req, adminJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void createModule_throws_whenCodeAlreadyExists() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(moduleRepository.existsByCourseIdAndCode(courseId, "JAVA_CORE")).thenReturn(true);

        CreateModuleRequest req = new CreateModuleRequest("JAVA_CORE", "Java Core", "Desc", 1);

        assertThatThrownBy(() -> moduleService.createModule(courseId, req, adminJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.MODULE_CODE_ALREADY_EXISTS);
    }

    @Test
    void getModulesByCourseId_returnsSortedList() {
        when(courseRepository.existsById(courseId)).thenReturn(true);
        Module m1 = Module.create(courseId, "M1", "Mod 1", "D1", 1, adminId);
        Module m2 = Module.create(courseId, "M2", "Mod 2", "D2", 2, adminId);
        when(moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId)).thenReturn(List.of(m1, m2));

        List<ModuleResponse> result = moduleService.getModulesByCourseId(courseId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("M1");
        assertThat(result.get(1).code()).isEqualTo("M2");
    }

    @Test
    void updateModule_success() {
        UUID moduleId = UUID.randomUUID();
        when(courseRepository.existsById(courseId)).thenReturn(true);
        Module module = Module.create(courseId, "JAVA_CORE", "Old Name", "Old Desc", 1, adminId);
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(Module.class))).thenAnswer(inv -> inv.getArgument(0));

        com.codegym.aiplanning.controller.curriculum.dto.UpdateModuleRequest req =
                new com.codegym.aiplanning.controller.curriculum.dto.UpdateModuleRequest("New Name", "New Desc");

        ModuleResponse response = moduleService.updateModule(courseId, moduleId, req, adminJwt);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("New Desc");
    }

    @Test
    void reorderModules_success() {
        when(courseRepository.existsById(courseId)).thenReturn(true);
        Module m1 = Module.create(courseId, "M1", "Mod 1", "D1", 1, adminId);
        Module m2 = Module.create(courseId, "M2", "Mod 2", "D2", 2, adminId);

        // Reflection to set IDs for m1 and m2
        org.springframework.test.util.ReflectionTestUtils.setField(m1, "id", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(m2, "id", UUID.randomUUID());

        when(moduleRepository.findByCourseIdOrderBySequenceNumberAsc(courseId)).thenReturn(List.of(m1, m2));

        var req = new com.codegym.aiplanning.controller.curriculum.dto.ReorderModulesRequest(List.of(
                new com.codegym.aiplanning.controller.curriculum.dto.ModuleOrderItem(m1.getId(), 2),
                new com.codegym.aiplanning.controller.curriculum.dto.ModuleOrderItem(m2.getId(), 1)
        ));

        List<ModuleResponse> response = moduleService.reorderModules(courseId, req, adminJwt);

        assertThat(response).hasSize(2);
    }

    @Test
    void deactivateModule_success() {
        UUID moduleId = UUID.randomUUID();
        when(courseRepository.existsById(courseId)).thenReturn(true);
        Module module = Module.create(courseId, "JAVA_CORE", "Name", "Desc", 1, adminId);
        org.springframework.test.util.ReflectionTestUtils.setField(module, "id", moduleId);
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(Module.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = moduleService.deactivateModule(courseId, moduleId, adminJwt);

        assertThat(response.status()).isEqualTo(com.codegym.aiplanning.entity.curriculum.ModuleStatus.INACTIVE);
    }

    @Test
    void activateModule_success() {
        UUID moduleId = UUID.randomUUID();
        when(courseRepository.existsById(courseId)).thenReturn(true);
        Module module = Module.create(courseId, "JAVA_CORE", "Name", "Desc", 1, adminId);
        module.deactivate(adminId);
        org.springframework.test.util.ReflectionTestUtils.setField(module, "id", moduleId);
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(Module.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = moduleService.activateModule(courseId, moduleId, adminJwt);

        assertThat(response.status()).isEqualTo(com.codegym.aiplanning.entity.curriculum.ModuleStatus.ACTIVE);
    }
}
