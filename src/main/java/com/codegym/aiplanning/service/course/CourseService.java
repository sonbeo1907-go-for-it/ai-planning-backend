package com.codegym.aiplanning.service.course;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.course.dto.CourseResponse;
import com.codegym.aiplanning.controller.course.dto.CourseSearchParam;
import com.codegym.aiplanning.controller.course.dto.CreateCourseRequest;
import com.codegym.aiplanning.controller.course.dto.UpdateCourseRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface CourseService {

    CourseResponse createCourse(CreateCourseRequest request, Jwt actorJwt);

    PageResponse<CourseResponse> getCourses(CourseSearchParam param);

    CourseResponse getCourse(UUID courseId);

    CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request, Jwt actorJwt);

    CourseResponse deactivateCourse(UUID courseId, Jwt actorJwt);

    CourseResponse activateCourse(UUID courseId, Jwt actorJwt);
}
