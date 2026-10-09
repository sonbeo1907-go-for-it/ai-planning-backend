package com.codegym.aiplanning.controller.billing;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.billing.dto.CreateTopUpOrderRequest;
import com.codegym.aiplanning.controller.billing.dto.CreditPackageResponse;
import com.codegym.aiplanning.controller.billing.dto.TopUpOrderResponse;
import com.codegym.aiplanning.service.billing.BillingOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.BILLING)
@Tag(name = "Billing", description = "Billing and Credit Package management APIs")
public class BillingController {

    private final BillingOrderService billingOrderService;

    public BillingController(BillingOrderService billingOrderService) {
        this.billingOrderService = billingOrderService;
    }

    @GetMapping("/packages")
    @Operation(summary = "Get list of active credit packages")
    public ApiResponse<List<CreditPackageResponse>> getActivePackages() {
        return ApiResponse.of(billingOrderService.getActivePackages());
    }

    @PostMapping("/top-up-orders")
    @Operation(summary = "Create top-up order and open payment checkout")
    public ApiResponse<TopUpOrderResponse> createTopUpOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTopUpOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(billingOrderService.createTopUpOrder(userId(jwt), request.packageId(), idempotencyKey));
    }

    @GetMapping("/top-up-orders/{orderId}")
    @Operation(summary = "Get top-up order details by orderId")
    public ApiResponse<TopUpOrderResponse> getTopUpOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(billingOrderService.getTopUpOrder(userId(jwt), orderId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
