package com.codegym.aiplanning.controller.admin.billing.dto;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AdminBillingDtos {
    private AdminBillingDtos() {}

    public record PackageInput(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String packageCode,
            @NotBlank @Size(max = 100) String name,
            @Positive @Max(9_007_199_254_740_991L) long priceVnd,
            @Positive @Max(9_007_199_254_740_991L) long baseCredits,
            @PositiveOrZero @Max(9_007_199_254_740_991L) long bonusCredits,
            @Min(0) int sortOrder,
            @PositiveOrZero Long version) {}

    public record PackageView(UUID id, long version, String packageCode, String name,
            long priceVnd, long baseCredits, long bonusCredits, long totalCredits,
            CreditPackageStatus status, int sortOrder) {
        public static PackageView from(CreditPackage value) {
            return new PackageView(value.getId(), value.getVersion(), value.getPackageCode(), value.getName(),
                    value.getPriceVnd(), value.getBaseCredits(), value.getBonusCredits(), value.getTotalCredits(),
                    value.getStatus(), value.getSortOrder());
        }
    }

    public record PackageStatusInput(@NotNull @PositiveOrZero Long version, @NotNull CreditPackageStatus status) {}

    public record RateInput(@NotNull AiPurpose purpose,
            @Size(max = 50) @Pattern(regexp = "\\s*([A-Za-z0-9_-]{1,50})?\\s*") String modelCategory,
            @Positive @Max(9_007_199_254_740_991L) long creditCost, @PositiveOrZero Long version) {}

    public record RateStatusInput(@NotNull @PositiveOrZero Long version, @NotNull AiCreditRateStatus status,
            UUID replacesRateId, @PositiveOrZero Long replacesRateVersion) {}

    public record RateActivationView(RateView selected, RateView current) {}

    public record RateView(UUID id, long version, AiPurpose purpose, String modelCategory,
            long creditCost, AiCreditRateStatus status, Instant effectiveFrom) {
        public static RateView from(AiCreditRate value) {
            return new RateView(value.getId(), value.getVersion(), value.getPurpose(), value.getModelCategory(),
                    value.getCreditCost(), value.getStatus(), value.getEffectiveFrom());
        }
    }

    public record WalletView(UUID userId, String email, UUID walletId, Long version,
            long availableCredits, long reservedCredits, boolean initialized) {}

    public record AdjustmentInput(
            @Min(-9_007_199_254_740_991L) @Max(9_007_199_254_740_991L) long delta,
            @NotBlank @Size(max = 200) String reason, @PositiveOrZero Long version) {}

    public record AdjustmentView(UUID ledgerEntryId, UUID userId, long delta,
            long availableCredits, long reservedCredits, Instant recordedAt, boolean replayed) {
        public static AdjustmentView from(CreditLedgerEntry entry, boolean replayed) {
            return new AdjustmentView(entry.getId(), entry.getUser().getId(), entry.getAvailableDelta(),
                    entry.getAvailableBalanceAfter(), entry.getReservedBalanceAfter(), entry.getRecordedAt(), replayed);
        }
    }
}
