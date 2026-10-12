package com.codegym.aiplanning.controller.admin.billing;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.controller.admin.billing.dto.AdminBillingDtos.*;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.service.billing.AdminBillingCatalogService;
import com.codegym.aiplanning.service.billing.AdminCreditAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/billing")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Billing Administration", description = "Packages, fixed AI prices and audited credit adjustments")
public class AdminBillingController {
    private final AdminBillingCatalogService catalog;
    private final AdminCreditAdjustmentService adjustments;

    public AdminBillingController(AdminBillingCatalogService catalog, AdminCreditAdjustmentService adjustments) {
        this.catalog = catalog;
        this.adjustments = adjustments;
    }

    @GetMapping("/packages")
    public ApiResponse<PageResponse<PackageView>> packages(
            @RequestParam(required = false) CreditPackageStatus status, Pageable page) {
        return ApiResponse.of(PageResponse.from(catalog.packages(status, page)));
    }

    @PostMapping("/packages")
    @Operation(summary = "Create an archived package; activate explicitly when ready")
    public ApiResponse<PackageView> createPackage(@Valid @RequestBody PackageInput input,
            @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.createPackage(id(actor), email(actor), input));
    }

    @PutMapping("/packages/{packageId}")
    public ApiResponse<PackageView> updatePackage(@PathVariable UUID packageId,
            @Valid @RequestBody PackageInput input, @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.updatePackage(id(actor), email(actor), packageId, input));
    }

    @PatchMapping("/packages/{packageId}/status")
    public ApiResponse<PackageView> packageStatus(@PathVariable UUID packageId,
            @Valid @RequestBody PackageStatusInput input, @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.packageStatus(id(actor), email(actor), packageId, input));
    }

    @GetMapping("/ai-rates")
    public ApiResponse<PageResponse<RateView>> rates(@RequestParam(required = false) AiPurpose purpose,
            @RequestParam(required = false) AiCreditRateStatus status, Pageable page) {
        return ApiResponse.of(PageResponse.from(catalog.rates(purpose, status, page)));
    }

    @PostMapping("/ai-rates")
    public ApiResponse<RateView> createRate(@Valid @RequestBody RateInput input, @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.createRate(id(actor), email(actor), input));
    }

    @GetMapping("/ai-rates/{rateId}/activation-preview")
    public ApiResponse<RateActivationView> activationPreview(@PathVariable UUID rateId) {
        return ApiResponse.of(catalog.activationPreview(rateId));
    }

    @PutMapping("/ai-rates/{rateId}")
    @Operation(summary = "Create a replacement price without editing historical rates")
    public ApiResponse<RateView> replaceRate(@PathVariable UUID rateId,
            @Valid @RequestBody RateInput input, @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.replaceRate(id(actor), email(actor), rateId, input));
    }

    @PatchMapping("/ai-rates/{rateId}/status")
    public ApiResponse<RateView> rateStatus(@PathVariable UUID rateId,
            @Valid @RequestBody RateStatusInput input, @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(catalog.rateStatus(id(actor), email(actor), rateId, input));
    }

    @GetMapping("/wallets/lookup")
    @Operation(summary = "Exact email/UUID lookup of billing identity only; never initializes a wallet")
    public ApiResponse<WalletView> lookup(@RequestParam String account) {
        return ApiResponse.of(adjustments.lookup(account));
    }

    @GetMapping("/wallets/{userId}")
    public ApiResponse<WalletView> wallet(@PathVariable UUID userId) {
        return ApiResponse.of(adjustments.get(userId));
    }

    @PostMapping("/wallets/{userId}/adjustments")
    @Operation(summary = "Idempotent available-credit adjustment with immutable ledger and audit evidence")
    public ApiResponse<AdjustmentView> adjust(@PathVariable UUID userId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AdjustmentInput input,
            @AuthenticationPrincipal Jwt actor) {
        return ApiResponse.of(adjustments.adjust(id(actor), email(actor), userId, key, input));
    }

    private UUID id(Jwt actor) {
        return UUID.fromString(actor.getSubject());
    }

    private String email(Jwt actor) {
        return actor.getClaimAsString("email");
    }
}
