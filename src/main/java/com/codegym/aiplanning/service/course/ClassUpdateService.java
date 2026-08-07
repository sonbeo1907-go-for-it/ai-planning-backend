package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import com.codegym.aiplanning.controller.course.dto.UpdateClassRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface ClassUpdateService {
    ClassResponse updateClass(UUID classId, UpdateClassRequest request, Jwt actorJwt);
}
