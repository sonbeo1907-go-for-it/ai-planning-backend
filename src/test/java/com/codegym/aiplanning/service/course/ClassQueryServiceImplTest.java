package com.codegym.aiplanning.service.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.ClassSearchParam;
import com.codegym.aiplanning.entity.course.ClassStatus;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.course.impl.ClassQueryServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ClassQueryServiceImplTest {

    @Mock
    private StudyClassRepository studyClassRepository;

    @InjectMocks
    private ClassQueryServiceImpl classQueryService;

    @Test
    void getClasses_WithAllParams() {
        // Arrange
        ClassSearchParam param = new ClassSearchParam(" Java ", ClassStatus.ACTIVE, 1, 5);

        StudyClass studyClass = mock(StudyClass.class);
        when(studyClass.getId()).thenReturn(UUID.randomUUID());
        when(studyClass.getCourseId()).thenReturn(UUID.randomUUID());
        when(studyClass.getCode()).thenReturn("JAV101");
        
        Page<StudyClass> mockPage = new PageImpl<>(List.of(studyClass));
        
        when(studyClassRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        PageResponse<ClassResponse> response = classQueryService.getClasses(param);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("JAV101", response.content().get(0).code());
        
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(studyClassRepository).findAll(any(Specification.class), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(1, capturedPageable.getPageNumber());
        assertEquals(5, capturedPageable.getPageSize());
        
        // verify deterministic sort
        assertNotNull(capturedPageable.getSort().getOrderFor("createdAt"));
        assertNotNull(capturedPageable.getSort().getOrderFor("id"));
    }

    @Test
    void getClasses_WithNullParams_UsesDefaults() {
        // Arrange
        ClassSearchParam param = new ClassSearchParam(null, null, null, null);

        Page<StudyClass> mockPage = new PageImpl<>(List.of());
        when(studyClassRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        classQueryService.getClasses(param);

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(studyClassRepository).findAll(any(Specification.class), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(10, capturedPageable.getPageSize());
    }
}
