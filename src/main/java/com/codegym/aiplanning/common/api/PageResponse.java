package com.codegym.aiplanning.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "Phản hồi dữ liệu danh sách có phân trang chuẩn hóa")
public record PageResponse<T>(
        @Schema(description = "Danh sách phần tử dữ liệu của trang hiện tại")
        List<T> content,

        @Schema(description = "Chỉ số trang hiện tại (0-indexed)", example = "0")
        int page,

        @Schema(description = "Kích thước trang", example = "10")
        int size,

        @Schema(description = "Tổng số phần tử trên toàn bộ hệ thống", example = "42")
        long totalElements,

        @Schema(description = "Tổng số trang", example = "5")
        int totalPages,

        @Schema(description = "Có phải trang đầu tiên không", example = "true")
        boolean first,

        @Schema(description = "Có phải trang cuối cùng không", example = "false")
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isFirst(),
                springPage.isLast());
    }
}

