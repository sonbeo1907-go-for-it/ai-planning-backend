package com.codegym.aiplanning.controller.billing;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.billing.dto.CreateTopUpOrderRequest;
import com.codegym.aiplanning.controller.billing.dto.CreditPackageResponse;
import com.codegym.aiplanning.controller.billing.dto.CreditWalletResponse;
import com.codegym.aiplanning.controller.billing.dto.TopUpOrderResponse;
import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.service.billing.BillingOrderService;
import com.codegym.aiplanning.service.billing.CreditWalletService;
import com.codegym.aiplanning.service.billing.VnpayIpnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codegym.aiplanning.controller.billing.dto.AiCreditRateResponse;
import com.codegym.aiplanning.controller.billing.dto.CreditTransactionResponse;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.service.billing.CreditReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import java.time.Instant;

@RestController
@RequestMapping(ApiConstant.BILLING)
@Tag(name = "Billing", description = "Billing and Credit Package management APIs")
public class BillingController {

    private final CreditWalletService creditWalletService;
    private final BillingOrderService billingOrderService;
    private final VnpayIpnService vnpayIpnService;
    private final CreditReservationService creditReservationService;

    public BillingController(
            CreditWalletService creditWalletService,
            BillingOrderService billingOrderService,
            VnpayIpnService vnpayIpnService,
            CreditReservationService creditReservationService) {
        this.creditWalletService = creditWalletService;
        this.billingOrderService = billingOrderService;
        this.vnpayIpnService = vnpayIpnService;
        this.creditReservationService = creditReservationService;
    }

    @GetMapping(ApiConstant.BILLING_WALLET)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Get or initialize the authenticated user's AI Credit wallet",
            description = "Returns the available and reserved AI credit balance for the authenticated user only. "
                    + "If the wallet does not exist yet, initializes it lazily and grants configured welcome credits.",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200", description = "Current credit wallet"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "Authentication is required or the session has expired.",
                        content = @Content(schema = @Schema(implementation = ApiError.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "403",
                        description = "Forbidden if authenticated user does not have USER role.",
                        content = @Content(schema = @Schema(implementation = ApiError.class)))
            })
    public ApiResponse<CreditWalletResponse> getWallet(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(creditWalletService.getOrCreateWallet(userId(jwt)));
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

    @GetMapping("/top-up-orders/by-code/{orderCode}")
    @Operation(summary = "Get top-up order details by orderCode")
    public ApiResponse<TopUpOrderResponse> getTopUpOrderByCode(
            @PathVariable String orderCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(billingOrderService.getTopUpOrderByCode(userId(jwt), orderCode));
    }

    @GetMapping(ApiConstant.BILLING_AI_PRICES)
    @Operation(summary = "Get list of active AI Credit prices per purpose/model")
    public ApiResponse<List<AiCreditRateResponse>> getAiPrices() {
        return ApiResponse.of(creditReservationService.getActiveRates());
    }

    @GetMapping(ApiConstant.BILLING_TRANSACTIONS)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get paginated credit transaction history of the authenticated user")
    public ApiResponse<Page<CreditTransactionResponse>> getTransactions(
            @RequestParam(required = false) LedgerEntryType entryType,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @PageableDefault(sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(creditWalletService.getTransactions(userId(jwt), entryType, fromDate, toDate, pageable));
    }

    @GetMapping(ApiConstant.BILLING_VNPAY_IPN)
    @Operation(summary = "VNPAY Instant Payment Notification webhook (GET)")
    public VnpayIpnResponse handleVnpayIpnGet(@RequestParam Map<String, String> params) {
        return vnpayIpnService.processIpn(params);
    }

    @PostMapping(ApiConstant.BILLING_VNPAY_IPN)
    @Operation(summary = "VNPAY Instant Payment Notification webhook (POST)")
    public VnpayIpnResponse handleVnpayIpnPost(@RequestParam Map<String, String> params) {
        return vnpayIpnService.processIpn(params);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
