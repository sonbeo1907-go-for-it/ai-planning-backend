package com.codegym.aiplanning.service.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.course.dto.CourseResponse;
import com.codegym.aiplanning.controller.course.dto.CourseSearchParam;
import com.codegym.aiplanning.controller.course.dto.CreateCourseRequest;
import com.codegym.aiplanning.controller.course.dto.UpdateCourseRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.entity.course.CourseStatus;
import com.codegym.aiplanning.repository.course.CourseRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.course.impl.CourseServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AuditLogService auditLogService;

    private CourseServiceImpl courseService;
    private UUID actorId;
    private Jwt actorJwt;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository, auditLogService);
        actorId = UUID.randomUUID();
        actorJwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(actorId.toString())
                .claim("preferred_username", "course_admin")
                .build();
    }

    @Test
    void createCourse_normalizesAndPersistsActiveCourse() {
        CreateCourseRequest request = new CreateCourseRequest(
                "fullstack_java", "  Fullstack Java  ", "  Java curriculum  ");
        when(courseRepository.existsByCodeIgnoreCase("FULLSTACK_JAVA")).thenReturn(false);
        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        CourseResponse response = courseService.createCourse(request, actorJwt);

        assertThat(response.code()).isEqualTo("FULLSTACK_JAVA");
        assertThat(response.name()).isEqualTo("Fullstack Java");
        assertThat(response.description()).isEqualTo("Java curriculum");
        assertThat(response.status()).isEqualTo(CourseStatus.ACTIVE);
        assertThat(response.createdBy()).isEqualTo(actorId);
        assertThat(response.updatedBy()).isEqualTo(actorId);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("course_admin"),
                eq(AuditEventAction.COURSE_CREATED),
                eq("COURSE"),
                eq(response.id().toString()),
                eq("Created course 'FULLSTACK_JAVA'"),
                isNull(),
                isNull());
    }

    @Test
    void createCourse_existingCode_throwsStableConflict() {
        when(courseRepository.existsByCodeIgnoreCase("FULLSTACK_JAVA")).thenReturn(true);

        assertBusinessError(
                () -> courseService.createCourse(
                        new CreateCourseRequest("FULLSTACK_JAVA", "Fullstack Java", null),
                        actorJwt),
                ErrorCode.COURSE_CODE_ALREADY_EXISTS);

        verify(courseRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCourse_uniqueConstraintRace_throwsStableConflict() {
        when(courseRepository.existsByCodeIgnoreCase("FULLSTACK_JAVA")).thenReturn(false);
        when(courseRepository.saveAndFlush(any(Course.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate code"));

        assertBusinessError(
                () -> courseService.createCourse(
                        new CreateCourseRequest("FULLSTACK_JAVA", "Fullstack Java", null),
                        actorJwt),
                ErrorCode.COURSE_CODE_ALREADY_EXISTS);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void getCourse_missing_throwsCourseNotFound() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertBusinessError(
                () -> courseService.getCourse(courseId), ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void getCourses_returnsFilteredPage() {
        Course course = persistentCourse(CourseStatus.ACTIVE);
        when(courseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(course)));

        PageResponse<CourseResponse> result = courseService.getCourses(
                new CourseSearchParam("java", CourseStatus.ACTIVE, 0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).code()).isEqualTo("FULLSTACK_JAVA");
    }

    @Test
    void updateCourse_changesOnlyMutableFieldsAndAudits() {
        Course course = persistentCourse(CourseStatus.ACTIVE);
        UUID courseId = course.getId();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        CourseResponse response = courseService.updateCourse(
                courseId,
                new UpdateCourseRequest("  Updated Java  ", "  Updated context  "),
                actorJwt);

        assertThat(response.code()).isEqualTo("FULLSTACK_JAVA");
        assertThat(response.name()).isEqualTo("Updated Java");
        assertThat(response.description()).isEqualTo("Updated context");
        assertThat(response.updatedBy()).isEqualTo(actorId);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("course_admin"),
                eq(AuditEventAction.COURSE_UPDATED),
                eq("COURSE"),
                eq(courseId.toString()),
                eq("Updated course 'FULLSTACK_JAVA'"),
                isNull(),
                isNull());
    }

    @Test
    void deactivateCourse_changesStatusAndIsIdempotent() {
        Course course = persistentCourse(CourseStatus.ACTIVE);
        UUID courseId = course.getId();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        CourseResponse first = courseService.deactivateCourse(courseId, actorJwt);

        assertThat(first.status()).isEqualTo(CourseStatus.INACTIVE);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("course_admin"),
                eq(AuditEventAction.COURSE_DEACTIVATED),
                eq("COURSE"),
                eq(courseId.toString()),
                eq("Deactivated course 'FULLSTACK_JAVA'"),
                isNull(),
                isNull());

        clearInvocations(courseRepository, auditLogService);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        CourseResponse repeated = courseService.deactivateCourse(courseId, actorJwt);

        assertThat(repeated.status()).isEqualTo(CourseStatus.INACTIVE);
        verify(courseRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void activateCourse_changesStatusAndAudits() {
        Course course = persistentCourse(CourseStatus.INACTIVE);
        UUID courseId = course.getId();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        CourseResponse response = courseService.activateCourse(courseId, actorJwt);

        assertThat(response.status()).isEqualTo(CourseStatus.ACTIVE);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("course_admin"),
                eq(AuditEventAction.COURSE_ACTIVATED),
                eq("COURSE"),
                eq(courseId.toString()),
                eq("Activated course 'FULLSTACK_JAVA'"),
                isNull(),
                isNull());
    }

    @Test
    void createCourse_withoutAuthentication_throwsAuthenticationRequired() {
        assertBusinessError(
                () -> courseService.createCourse(
                        new CreateCourseRequest("FULLSTACK_JAVA", "Fullstack Java", null),
                        null),
                ErrorCode.AUTHENTICATION_REQUIRED);
        verifyNoInteractions(courseRepository, auditLogService);
    }

    private Course persistentCourse(CourseStatus status) {
        Course course = Course.create(
                "FULLSTACK_JAVA",
                "Fullstack Java",
                "Java curriculum",
                UUID.randomUUID());
        if (status == CourseStatus.INACTIVE) {
            course.deactivate(UUID.randomUUID());
        }
        return persisted(course);
    }

    private Course persisted(Course course) {
        if (course.getId() == null) {
            ReflectionTestUtils.setField(course, "id", UUID.randomUUID());
        }
        ReflectionTestUtils.setField(course, "createdAt", Instant.now());
        ReflectionTestUtils.setField(course, "updatedAt", Instant.now());
        return course;
    }

    private void assertBusinessError(Runnable action, ErrorCode expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(expectedCode);
    }
}
