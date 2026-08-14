package com.codegym.aiplanning.controller.material;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.material.dto.CreateTextMaterialRequest;
import com.codegym.aiplanning.controller.material.dto.MaterialResponse;
import com.codegym.aiplanning.service.material.MaterialService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import com.codegym.aiplanning.controller.material.dto.MaterialListResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiConstant.MATERIALS)
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Upload a learning material",
            description = "Uploads a PDF, DOCX, or TXT file (max 20MB) to be used as learning material.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "201", description = "File uploaded successfully"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "400",
                        description = "Invalid file type or empty file",
                        content = @Content(schema = @Schema(implementation = ApiError.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "413",
                        description = "Payload too large (exceeds 20MB)",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<MaterialResponse> uploadMaterial(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        MaterialResponse response = materialService.uploadMaterial(userId, file);
        return ApiResponse.of(response);
    }

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Create learning material from text",
            description = "Creates a TEXT or GOAL_DESCRIPTION learning material.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "201", description = "Material created successfully"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "400",
                        description = "Invalid text length or invalid type",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<MaterialResponse> createFromText(
            @Valid @RequestBody CreateTextMaterialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        MaterialResponse response = materialService.createFromText(userId, request);
        return ApiResponse.of(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Get my learning materials",
            description = "Returns a paginated list of learning materials owned by the current user. Archived materials are excluded.")
    public ApiResponse<Page<MaterialListResponse>> getMyMaterials(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Page<MaterialListResponse> response = materialService.getMyMaterials(userId, pageable);
        return ApiResponse.of(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Archive a learning material",
            description = "Soft-deletes the material so it won't appear in lists, but keeps it for roadmap reference.")
    public void archiveMaterial(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        materialService.archiveMaterial(userId, id);
    }
}
