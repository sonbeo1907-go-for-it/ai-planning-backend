package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.course.ClassQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClassQueryServiceImpl implements ClassQueryService {

    private final StudyClassRepository studyClassRepository;

    public ClassQueryServiceImpl(StudyClassRepository studyClassRepository) {
        this.studyClassRepository = studyClassRepository;
    }

    @Override
    public PageResponse<ClassResponse> getClasses(Pageable pageable) {
        Page<StudyClass> page = studyClassRepository.findAll(pageable);
        return PageResponse.from(page.map(ClassResponse::from));
    }
}
