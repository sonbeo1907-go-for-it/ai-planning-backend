package com.codegym.aiplanning.service.course.impl;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.ClassSearchParam;
import com.codegym.aiplanning.entity.course.StudyClass;
import com.codegym.aiplanning.repository.course.StudyClassRepository;
import com.codegym.aiplanning.service.course.ClassQueryService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public PageResponse<ClassResponse> getClasses(ClassSearchParam param) {
        Pageable pageable = PageRequest.of(
                param.resolvedPage(),
                param.resolvedSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Specification<StudyClass> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (param.search() != null && !param.search().trim().isEmpty()) {
                String search = "%" + param.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), search),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), search)));
            }
            if (param.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), param.status()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<StudyClass> page = studyClassRepository.findAll(specification, pageable);
        return PageResponse.from(page.map(ClassResponse::from));
    }
}
