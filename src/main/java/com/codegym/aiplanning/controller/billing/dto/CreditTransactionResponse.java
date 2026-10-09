package com.codegym.aiplanning.controller.billing.dto;

import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin chi tiết một giao dịch trong lịch sử AI Credit của người dùng")
public record CreditTransactionResponse(
        @Schema(description = "ID bản ghi ledger duy nhất")
        UUID id,

        @Schema(description = "Thời gian ghi nhận giao dịch (UTC)")
        Instant recordedAt,

        @Schema(description = "Loại giao dịch (TOP_UP, WELCOME_BONUS, RESERVE, USAGE, RELEASE_RESERVE, REFUND, ADJUSTMENT)")
        LedgerEntryType entryType,

        @Schema(description = "Lượng credit của giao dịch")
        long amount,

        @Schema(description = "Biến động số dư khả dụng (có dấu âm/dương)")
        long availableDelta,

        @Schema(description = "Biến động số dư giữ chỗ (có dấu âm/dương)")
        long reservedDelta,

        @Schema(description = "Số dư khả dụng sau giao dịch")
        long availableBalanceAfter,

        @Schema(description = "Số dư giữ chỗ sau giao dịch")
        long reservedBalanceAfter,

        @Schema(description = "Tổng số dư (khả dụng + giữ chỗ) sau giao dịch")
        long totalBalanceAfter,

        @Schema(description = "Loại thực thể tham chiếu (AI_EXECUTION, ORDER, ACCOUNT, RESERVATION, ADJUSTMENT)")
        LedgerReferenceType referenceType,

        @Schema(description = "ID thực thể tham chiếu")
        UUID referenceId,

        @Schema(description = "Mô tả nội dung giao dịch thân thiện với người dùng")
        String description,

        @Schema(description = "Trạng thái giao dịch (RESERVED, SETTLED, RELEASED, COMPLETED)")
        String status
) {
    public static CreditTransactionResponse from(CreditLedgerEntry entry) {
        long amount = Math.abs(entry.getAvailableDelta() != 0 ? entry.getAvailableDelta() : entry.getReservedDelta());
        String status = deriveStatus(entry.getEntryType());

        return new CreditTransactionResponse(
                entry.getId(),
                entry.getRecordedAt(),
                entry.getEntryType(),
                amount,
                entry.getAvailableDelta(),
                entry.getReservedDelta(),
                entry.getAvailableBalanceAfter(),
                entry.getReservedBalanceAfter(),
                entry.getAvailableBalanceAfter() + entry.getReservedBalanceAfter(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getDescription(),
                status
        );
    }

    private static String deriveStatus(LedgerEntryType type) {
        return switch (type) {
            case RESERVE -> "RESERVED";
            case RELEASE_RESERVE -> "RELEASED";
            case USAGE -> "SETTLED";
            case TOP_UP, WELCOME_BONUS, REFUND, ADJUSTMENT -> "COMPLETED";
        };
    }
}
