package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.CreateClassRequest;
import org.springframework.security.oauth2.jwt.Jwt;

public interface ClassCreationService {
    ClassResponse createClass(CreateClassRequest request, Jwt actorJwt);
}
