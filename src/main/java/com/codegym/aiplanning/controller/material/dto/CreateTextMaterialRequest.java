package com.codegym.aiplanning.controller.material.dto;

import com.codegym.aiplanning.entity.material.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateTextMaterialRequest {

    @NotNull(message = "Type is required")
    private MaterialType type;

    @NotBlank(message = "Content is required")
    @Size(min = 50, max = 50000, message = "Content must be between 50 and 50,000 characters")
    private String content;

    public MaterialType getType() {
        return type;
    }

    public void setType(MaterialType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
