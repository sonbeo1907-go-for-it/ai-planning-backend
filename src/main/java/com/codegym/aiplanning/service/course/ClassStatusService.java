package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.controller.course.dto.ChangeClassStatusRequest;
import com.codegym.aiplanning.controller.course.dto.ClassResponse;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface ClassStatusService {
    ClassResponse changeStatus(UUID classId, ChangeClassStatusRequest request, Jwt actorJwt);
}
