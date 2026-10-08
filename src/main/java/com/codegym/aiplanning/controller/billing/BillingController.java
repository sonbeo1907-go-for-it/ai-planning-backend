package com.codegym.aiplanning.controller.billing;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.billing.dto.CreditWalletResponse;
import com.codegym.aiplanning.service.billing.CreditWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.BILLING)
public class BillingController {

    private final CreditWalletService creditWalletService;

    public BillingController(CreditWalletService creditWalletService) {
        this.creditWalletService = creditWalletService;
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
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.of(creditWalletService.getOrCreateWallet(userId));
    }
}
