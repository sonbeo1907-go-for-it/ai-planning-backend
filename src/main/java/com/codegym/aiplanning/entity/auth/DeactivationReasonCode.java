package com.codegym.aiplanning.entity.auth;

public enum DeactivationReasonCode {
    POLICY_VIOLATION("Vi phạm chính sách sử dụng"),
    SECURITY_CONCERN("Vấn đề bảo mật"),
    EMPLOYMENT_ENDED("Kết thúc quá trình học/làm việc"),
    OTHER("Lý do khác");

    private final String displayName;

    DeactivationReasonCode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
