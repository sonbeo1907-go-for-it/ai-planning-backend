package com.codegym.aiplanning.service.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.course.dto.ChangeClassStatusRequest;
import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.CourseStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.entity.course.Course;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.repository.course.CourseRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ClassStatusServiceIntegrationTest {

    @Autowired
    private ClassStatusService classStatusService;

    @Autowired
    private StudyClassRepository studyClassRepository;

    @Autowired
    private CourseRepository courseRepository;

    private UUID classId;
    private Jwt adminA;
    private Jwt adminB;

    @BeforeEach
    void setUp() {
        Course course = Course.create("TEST-CRS", "Test Course", "Desc", UUID.randomUUID());
        courseRepository.save(course);

        StudyClass studyClass = StudyClass.create(course.getId(), "TEST-CLS", "Test Class", "Desc", UUID.randomUUID());
        StudyClass savedClass = studyClassRepository.save(studyClass);
        classId = savedClass.getId();

        adminA = mockJwt("adminA");
        adminB = mockJwt("adminB");
    }

    @AfterEach
    void tearDown() {
        studyClassRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private Jwt mockJwt(String username) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(jwt.getClaimAsString("preferred_username")).thenReturn(username);
        return jwt;
    }

    @Test
    void testOptimisticLocking_ConcurrentStatusUpdate() {
        // Admin A and B both see the class at version 0
        StudyClass classForA = studyClassRepository.findById(classId).orElseThrow();
        StudyClass classForB = studyClassRepository.findById(classId).orElseThrow();
        
        assertEquals(0L, classForA.getVersion());
        assertEquals(0L, classForB.getVersion());

        // Admin A changes status to ACTIVE successfully
        ChangeClassStatusRequest requestA = new ChangeClassStatusRequest(ClassStatus.ACTIVE, classForA.getVersion());
        classStatusService.changeStatus(classId, requestA, adminA);

        // At this point, the version in DB is 1. 
        // Admin B (still holding version 0) tries to change to CLOSED
        // Wait, PLANNED -> CLOSED is invalid anyway. Let's say Admin B also tries to change to ACTIVE
        // or let's assume Admin A changed something else, and Admin B tries to change status.
        // The point is version 0 != version 1 in DB, so it should fail with CONCURRENT_MODIFICATION
        ChangeClassStatusRequest requestB = new ChangeClassStatusRequest(ClassStatus.ACTIVE, classForB.getVersion());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            classStatusService.changeStatus(classId, requestB, adminB);
        });

        assertEquals(ErrorCode.CONCURRENT_MODIFICATION, exception.errorCode());
    }
}
